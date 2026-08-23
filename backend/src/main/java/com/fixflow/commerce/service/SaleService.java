package com.fixflow.commerce.service;

import com.fixflow.audit.service.AuditAction;
import com.fixflow.audit.service.AuditService;
import com.fixflow.catalog.domain.ProductVariant;
import com.fixflow.catalog.repository.ProductVariantRepository;
import com.fixflow.commerce.domain.Payment;
import com.fixflow.commerce.domain.PaymentMethod;
import com.fixflow.commerce.domain.PaymentReferenceType;
import com.fixflow.commerce.domain.PaymentStatus;
import com.fixflow.commerce.domain.Sale;
import com.fixflow.commerce.domain.SaleItem;
import com.fixflow.commerce.domain.SaleStatus;
import com.fixflow.commerce.dto.CommerceDtos.CreateSaleRequest;
import com.fixflow.commerce.dto.CommerceDtos.PaymentRequest;
import com.fixflow.commerce.dto.CommerceDtos.PaymentResponse;
import com.fixflow.commerce.dto.CommerceDtos.SaleItemResponse;
import com.fixflow.commerce.dto.CommerceDtos.SaleLineRequest;
import com.fixflow.commerce.dto.CommerceDtos.SaleResponse;
import com.fixflow.commerce.dto.CommerceDtos.VoidSaleRequest;
import com.fixflow.commerce.repository.PaymentRepository;
import com.fixflow.commerce.repository.SaleItemRepository;
import com.fixflow.commerce.repository.SaleRepository;
import com.fixflow.common.error.ApiException;
import com.fixflow.inventory.domain.InventoryReferenceType;
import com.fixflow.inventory.domain.InventoryTransactionType;
import com.fixflow.inventory.service.InventoryService;
import com.fixflow.inventory.service.StockMovement;
import com.fixflow.party.domain.Customer;
import com.fixflow.party.domain.CustomerType;
import com.fixflow.party.repository.CustomerRepository;
import com.fixflow.party.service.PartyService;
import com.fixflow.pricing.domain.PricingFlag;
import com.fixflow.pricing.service.PriceContext;
import com.fixflow.pricing.service.PriceQuote;
import com.fixflow.pricing.service.PricingService;
import com.fixflow.config.FixFlowProperties;
import com.fixflow.shop.domain.Shop;
import com.fixflow.shop.repository.ShopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SaleService {

    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final PaymentRepository paymentRepository;
    private final ProductVariantRepository variantRepository;
    private final CustomerRepository customerRepository;
    private final ShopRepository shopRepository;
    private final PricingService pricingService;
    private final InventoryService inventoryService;
    private final AuditService auditService;
    private final FixFlowProperties properties;

    @Transactional
    public SaleResponse complete(UUID shopId, CreateSaleRequest request) {
        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            var existing = saleRepository.findByShopIdAndIdempotencyKey(shopId, request.idempotencyKey());
            if (existing.isPresent()) {
                return toResponse(existing.get());
            }
        }

        Customer customer = request.customerId() == null ? null
                : customerRepository.findByIdAndShopId(request.customerId(), shopId)
                .orElseThrow(() -> ApiException.notFound("Customer", request.customerId()));
        CustomerType customerType = customer == null ? CustomerType.RETAIL : customer.getCustomerType();
        PricingFlag flag = request.pricingFlag() == null ? PricingFlag.NORMAL : request.pricingFlag();

        Sale sale = new Sale();
        sale.setShopId(shopId);
        sale.setCustomerId(customer == null ? null : customer.getId());
        sale.setInvoiceNumber(nextInvoiceNumber(shopId));
        sale.setStatus(SaleStatus.COMPLETED);
        sale.setPricingFlag(flag);
        sale.setNotes(request.notes());
        sale.setIdempotencyKey(blankToNull(request.idempotencyKey()));
        sale.setDeviceId(request.deviceId());
        sale.setOccurredAt(Instant.now());
        saleRepository.save(sale);

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal profit = BigDecimal.ZERO;
        List<SaleItem> items = new ArrayList<>();

        for (SaleLineRequest line : request.items()) {
            ProductVariant variant = variantRepository.findByIdAndShopId(line.variantId(), shopId)
                    .orElseThrow(() -> ApiException.notFound("Product variant", line.variantId()));
            PriceQuote quote = pricingService.quote(PriceContext.of(shopId, variant, flag)
                    .withCustomerType(customerType)
                    .withQuantity(line.quantity()));
            BigDecimal unitPrice = line.unitPrice() == null ? quote.unitPrice() : line.unitPrice();
            pricingService.assertAboveMinimum(variant, unitPrice);
            BigDecimal discount = PartyService.nz(line.discount());
            BigDecimal lineGross = unitPrice.multiply(BigDecimal.valueOf(line.quantity()));
            BigDecimal lineTotal = lineGross.subtract(discount).max(BigDecimal.ZERO);
            BigDecimal lineCost = quote.costPrice().multiply(BigDecimal.valueOf(line.quantity()));
            BigDecimal taxRate = variant.getProduct() == null || variant.getProduct().getTaxRate() == null
                    ? BigDecimal.ZERO : variant.getProduct().getTaxRate();
            BigDecimal lineTax = lineTotal.multiply(taxRate)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            SaleItem item = new SaleItem();
            item.setShopId(shopId);
            item.setSaleId(sale.getId());
            item.setProductVariantId(variant.getId());
            item.setQuantity(line.quantity());
            item.setUnitPrice(unitPrice);
            item.setUnitCost(quote.costPrice());
            item.setDiscount(discount);
            item.setTaxRate(taxRate);
            item.setLineTotal(lineTotal);
            item.setProfit(lineTotal.subtract(lineCost));
            saleItemRepository.save(item);
            items.add(item);

            inventoryService.post(shopId, StockMovement.of(variant.getId(), InventoryTransactionType.OUT, line.quantity())
                    .reference(InventoryReferenceType.SALE, sale.getId(), sale.getInvoiceNumber())
                    .idempotencyKey(sale.getId() + ":" + variant.getId())
                    .build());

            subtotal = subtotal.add(lineTotal);
            tax = tax.add(lineTax);
            profit = profit.add(item.getProfit());
        }

        BigDecimal headerDiscount = PartyService.nz(request.discount());
        BigDecimal total = subtotal.add(tax).subtract(headerDiscount).max(BigDecimal.ZERO);
        List<Payment> payments = capturePayments(shopId, PaymentReferenceType.SALE, sale.getId(), request.payments());
        BigDecimal paid = payments.stream().map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        sale.setSubtotal(subtotal);
        sale.setDiscount(headerDiscount);
        sale.setTax(tax);
        sale.setTotal(total);
        sale.setPaid(paid.min(total));
        sale.setOutstanding(total.subtract(sale.getPaid()).max(BigDecimal.ZERO));
        sale.setProfit(profit.subtract(headerDiscount));
        saleRepository.save(sale);

        if (customer != null) {
            customer.setTotalPurchases(PartyService.nz(customer.getTotalPurchases()).add(total));
            customer.setOutstandingAmount(PartyService.nz(customer.getOutstandingAmount()).add(sale.getOutstanding()));
            customer.setLastTransactionAt(sale.getOccurredAt());
            customerRepository.save(customer);
        }

        auditService.record(AuditAction.SALE_COMPLETED, "Sale", sale.getId(),
                "Sale %s for %s".formatted(sale.getInvoiceNumber(), total.toPlainString()));
        return toResponse(sale, items, payments);
    }

    @Transactional
    public SaleResponse voidSale(UUID shopId, UUID saleId, VoidSaleRequest request) {
        Sale sale = require(shopId, saleId);
        if (sale.getStatus() == SaleStatus.VOID) {
            throw ApiException.businessRule("This sale is already void.");
        }
        List<SaleItem> items = saleItemRepository.findBySaleIdOrderByCreatedAtAsc(sale.getId());
        for (SaleItem item : items) {
            inventoryService.post(shopId, StockMovement.of(item.getProductVariantId(),
                            InventoryTransactionType.RETURN, item.getQuantity())
                    .reference(InventoryReferenceType.SALE_RETURN, sale.getId(), sale.getInvoiceNumber())
                    .reason(request == null ? "Void" : request.reason())
                    .build());
        }
        sale.setStatus(SaleStatus.VOID);
        sale.setVoidedAt(Instant.now());
        sale.setVoidReason(request == null ? null : request.reason());
        saleRepository.save(sale);

        if (sale.getCustomerId() != null) {
            customerRepository.findByIdAndShopId(sale.getCustomerId(), shopId).ifPresent(customer -> {
                customer.setTotalPurchases(PartyService.nz(customer.getTotalPurchases()).subtract(sale.getTotal())
                        .max(BigDecimal.ZERO));
                customer.setOutstandingAmount(PartyService.nz(customer.getOutstandingAmount())
                        .subtract(sale.getOutstanding()).max(BigDecimal.ZERO));
                customerRepository.save(customer);
            });
        }
        auditService.record(AuditAction.SALE_VOIDED, "Sale", saleId,
                "Voided sale %s".formatted(sale.getInvoiceNumber()));
        return toResponse(sale);
    }

    @Transactional(readOnly = true)
    public Page<SaleResponse> list(UUID shopId, UUID customerId, Pageable pageable) {
        Page<Sale> page = customerId == null
                ? saleRepository.findByShopIdOrderByOccurredAtDesc(shopId, pageable)
                : saleRepository.findByShopIdAndCustomerIdOrderByOccurredAtDesc(shopId, customerId, pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public SaleResponse get(UUID shopId, UUID id) {
        return toResponse(require(shopId, id));
    }

    @Transactional(readOnly = true)
    public String invoiceHtml(UUID shopId, UUID id) {
        Sale sale = require(shopId, id);
        Shop shop = shopRepository.findById(shopId).orElseThrow();
        List<SaleItem> items = saleItemRepository.findBySaleIdOrderByCreatedAtAsc(sale.getId());
        StringBuilder rows = new StringBuilder();
        for (SaleItem item : items) {
            ProductVariant variant = variantRepository.findById(item.getProductVariantId()).orElse(null);
            String name = variant == null ? item.getProductVariantId().toString() : variant.getVariantName();
            rows.append("<tr><td>").append(escape(name)).append("</td><td>")
                    .append(item.getQuantity()).append("</td><td>")
                    .append(item.getUnitPrice()).append("</td><td>")
                    .append(item.getLineTotal()).append("</td></tr>");
        }
        String customerName = sale.getCustomerId() == null ? "Walk-in"
                : customerRepository.findById(sale.getCustomerId()).map(Customer::getName).orElse("Walk-in");
        return """
                <html><head><title>%s</title>
                <style>body{font-family:sans-serif;padding:24px}table{width:100%%;border-collapse:collapse}
                td,th{border-bottom:1px solid #ddd;padding:8px;text-align:left}</style></head>
                <body><h1>%s</h1><p>%s · %s · %s</p>
                <p>Invoice <strong>%s</strong> · %s · %s</p>
                <table><thead><tr><th>Item</th><th>Qty</th><th>Price</th><th>Total</th></tr></thead>
                <tbody>%s</tbody></table>
                <p>Subtotal %s · Tax %s · Discount %s</p>
                <h2>Total %s</h2><p>Paid %s · Outstanding %s</p>
                <p>Customer %s</p>
                <p>Warranty as discussed at the counter.</p>
                <p style="margin-top:32px;color:#888;font-size:12px">%s · %s<br/>%s<br/>© %s %s</p>
                </body></html>
                """.formatted(sale.getInvoiceNumber(), escape(shop.getName()),
                escape(nullToEmpty(shop.getAddressLine1())), escape(nullToEmpty(shop.getCity())),
                escape(nullToEmpty(shop.getPhone())), sale.getInvoiceNumber(), sale.getStatus(),
                sale.getOccurredAt(), rows, sale.getSubtotal(), sale.getTax(), sale.getDiscount(),
                sale.getTotal(), sale.getPaid(), sale.getOutstanding(), escape(customerName),
                escape(properties.getBrand().getProduct()), escape(properties.getBrand().getTagline()),
                escape(properties.getPlatform().getPublicOrigin()),
                properties.getBrand().getCopyrightYear(), escape(properties.getBrand().getOrganization()));
    }

    private List<Payment> capturePayments(UUID shopId, PaymentReferenceType type, UUID referenceId,
                                          List<PaymentRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        List<Payment> saved = new ArrayList<>();
        for (PaymentRequest request : requests) {
            Payment payment = new Payment();
            payment.setShopId(shopId);
            payment.setReferenceType(type);
            payment.setReferenceId(referenceId);
            payment.setMethod(request.method() == null ? PaymentMethod.CASH : request.method());
            payment.setAmount(request.amount());
            payment.setStatus(PaymentStatus.CAPTURED);
            payment.setNotes(request.notes());
            payment.setOccurredAt(Instant.now());
            paymentRepository.save(payment);
            saved.add(payment);
        }
        return saved;
    }

    private String nextInvoiceNumber(UUID shopId) {
        Shop shop = shopRepository.findByIdForUpdate(shopId)
                .orElseThrow(() -> ApiException.notFound("Shop", shopId));
        long next = shop.getInvoiceNextNumber();
        shop.setInvoiceNextNumber(next + 1);
        shopRepository.save(shop);
        return shop.getInvoicePrefix() + String.format("%06d", next);
    }

    private Sale require(UUID shopId, UUID id) {
        return saleRepository.findByIdAndShopId(id, shopId)
                .orElseThrow(() -> ApiException.notFound("Sale", id));
    }

    private SaleResponse toResponse(Sale sale) {
        return toResponse(sale, saleItemRepository.findBySaleIdOrderByCreatedAtAsc(sale.getId()),
                paymentRepository.findByShopIdAndReferenceTypeAndReferenceIdOrderByOccurredAtAsc(
                        sale.getShopId(), PaymentReferenceType.SALE, sale.getId()));
    }

    private SaleResponse toResponse(Sale sale, List<SaleItem> items, List<Payment> payments) {
        String customerName = sale.getCustomerId() == null ? null
                : customerRepository.findById(sale.getCustomerId()).map(Customer::getName).orElse(null);
        return new SaleResponse(sale.getId(), sale.getInvoiceNumber(), sale.getStatus(), sale.getCustomerId(),
                customerName, sale.getPricingFlag(), sale.getSubtotal(), sale.getDiscount(), sale.getTax(),
                sale.getTotal(), sale.getPaid(), sale.getOutstanding(), sale.getProfit(), sale.getNotes(),
                sale.getOccurredAt(), items.stream().map(this::toItem).toList(),
                payments.stream().map(this::toPayment).toList());
    }

    private SaleItemResponse toItem(SaleItem item) {
        String name = variantRepository.findById(item.getProductVariantId())
                .map(ProductVariant::getVariantName).orElse(null);
        return new SaleItemResponse(item.getId(), item.getProductVariantId(), name, item.getQuantity(),
                item.getUnitPrice(), item.getUnitCost(), item.getDiscount(), item.getLineTotal(), item.getProfit());
    }

    PaymentResponse toPayment(Payment payment) {
        return new PaymentResponse(payment.getId(), payment.getMethod(), payment.getAmount(),
                payment.getStatus(), payment.getOccurredAt(), payment.getNotes());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
