package com.fixflow.commerce.service;

import com.fixflow.audit.service.AuditAction;
import com.fixflow.audit.service.AuditService;
import com.fixflow.catalog.domain.ProductVariant;
import com.fixflow.catalog.repository.ProductVariantRepository;
import com.fixflow.commerce.domain.Payment;
import com.fixflow.commerce.domain.PaymentMethod;
import com.fixflow.commerce.domain.PaymentReferenceType;
import com.fixflow.commerce.domain.PaymentStatus;
import com.fixflow.commerce.domain.Purchase;
import com.fixflow.commerce.domain.PurchaseItem;
import com.fixflow.commerce.domain.PurchaseStatus;
import com.fixflow.commerce.dto.CommerceDtos.CreatePurchaseRequest;
import com.fixflow.commerce.dto.CommerceDtos.PaymentRequest;
import com.fixflow.commerce.dto.CommerceDtos.PaymentResponse;
import com.fixflow.commerce.dto.CommerceDtos.PurchaseItemResponse;
import com.fixflow.commerce.dto.CommerceDtos.PurchaseLineRequest;
import com.fixflow.commerce.dto.CommerceDtos.PurchaseResponse;
import com.fixflow.commerce.repository.PaymentRepository;
import com.fixflow.commerce.repository.PurchaseItemRepository;
import com.fixflow.commerce.repository.PurchaseRepository;
import com.fixflow.common.error.ApiException;
import com.fixflow.inventory.domain.InventoryReferenceType;
import com.fixflow.inventory.domain.InventoryTransactionType;
import com.fixflow.inventory.service.InventoryService;
import com.fixflow.inventory.service.StockMovement;
import com.fixflow.party.domain.Supplier;
import com.fixflow.party.repository.SupplierRepository;
import com.fixflow.party.service.PartyService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PurchaseService {

    private final PurchaseRepository purchaseRepository;
    private final PurchaseItemRepository purchaseItemRepository;
    private final PaymentRepository paymentRepository;
    private final ProductVariantRepository variantRepository;
    private final SupplierRepository supplierRepository;
    private final InventoryService inventoryService;
    private final AuditService auditService;

    @Transactional
    public PurchaseResponse receive(UUID shopId, CreatePurchaseRequest request) {
        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            var existing = purchaseRepository.findByShopIdAndIdempotencyKey(shopId, request.idempotencyKey());
            if (existing.isPresent()) {
                return toResponse(existing.get());
            }
        }
        Supplier supplier = supplierRepository.findByIdAndShopId(request.supplierId(), shopId)
                .orElseThrow(() -> ApiException.notFound("Supplier", request.supplierId()));

        Purchase purchase = new Purchase();
        purchase.setShopId(shopId);
        purchase.setSupplierId(supplier.getId());
        purchase.setStatus(PurchaseStatus.RECEIVED);
        purchase.setNotes(request.notes());
        purchase.setIdempotencyKey(request.idempotencyKey());
        purchase.setReceivedAt(Instant.now());
        purchaseRepository.save(purchase);

        BigDecimal subtotal = BigDecimal.ZERO;
        List<PurchaseItem> items = new ArrayList<>();
        for (PurchaseLineRequest line : request.items()) {
            ProductVariant variant = variantRepository.findByIdAndShopId(line.variantId(), shopId)
                    .orElseThrow(() -> ApiException.notFound("Product variant", line.variantId()));
            BigDecimal unitCost = line.unitCost() == null ? variant.getCostPrice() : line.unitCost();
            BigDecimal lineTotal = unitCost.multiply(BigDecimal.valueOf(line.quantity()));

            PurchaseItem item = new PurchaseItem();
            item.setShopId(shopId);
            item.setPurchaseId(purchase.getId());
            item.setProductVariantId(variant.getId());
            item.setQuantity(line.quantity());
            item.setUnitCost(unitCost);
            item.setLineTotal(lineTotal);
            item.setBatchNo(line.batchNo());
            purchaseItemRepository.save(item);
            items.add(item);

            inventoryService.post(shopId, StockMovement.of(variant.getId(), InventoryTransactionType.IN, line.quantity())
                    .unitCost(unitCost)
                    .batchNo(line.batchNo())
                    .reference(InventoryReferenceType.PURCHASE, purchase.getId(), supplier.getName())
                    .idempotencyKey(purchase.getId() + ":" + variant.getId())
                    .build());
            subtotal = subtotal.add(lineTotal);
        }

        BigDecimal tax = PartyService.nz(request.tax());
        BigDecimal total = subtotal.add(tax);
        List<Payment> payments = capture(shopId, purchase.getId(), request.payments());
        BigDecimal paid = payments.stream().map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        purchase.setSubtotal(subtotal);
        purchase.setTax(tax);
        purchase.setTotal(total);
        purchase.setPaid(paid.min(total));
        purchase.setOutstanding(total.subtract(purchase.getPaid()).max(BigDecimal.ZERO));
        purchaseRepository.save(purchase);

        supplier.setOutstandingAmount(PartyService.nz(supplier.getOutstandingAmount()).add(purchase.getOutstanding()));
        supplierRepository.save(supplier);

        auditService.record(AuditAction.PURCHASE_RECEIVED, "Purchase", purchase.getId(),
                "Received purchase from %s for %s".formatted(supplier.getName(), total.toPlainString()));
        return toResponse(purchase, supplier.getName(), items, payments);
    }

    @Transactional
    public PurchaseResponse cancel(UUID shopId, UUID id, String reason) {
        Purchase purchase = require(shopId, id);
        if (purchase.getStatus() == PurchaseStatus.CANCELLED) {
            throw ApiException.businessRule("This purchase is already cancelled.");
        }
        for (PurchaseItem item : purchaseItemRepository.findByPurchaseIdOrderByCreatedAtAsc(purchase.getId())) {
            inventoryService.issue(shopId, item.getProductVariantId(), item.getQuantity(),
                    InventoryReferenceType.PURCHASE_RETURN, purchase.getId(), reason);
        }
        purchase.setStatus(PurchaseStatus.CANCELLED);
        purchaseRepository.save(purchase);
        supplierRepository.findByIdAndShopId(purchase.getSupplierId(), shopId).ifPresent(supplier -> {
            supplier.setOutstandingAmount(PartyService.nz(supplier.getOutstandingAmount())
                    .subtract(purchase.getOutstanding()).max(BigDecimal.ZERO));
            supplierRepository.save(supplier);
        });
        auditService.record(AuditAction.PURCHASE_CANCELLED, "Purchase", id, "Cancelled purchase");
        return toResponse(purchase);
    }

    @Transactional(readOnly = true)
    public Page<PurchaseResponse> list(UUID shopId, Pageable pageable) {
        return purchaseRepository.findByShopIdOrderByReceivedAtDesc(shopId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public PurchaseResponse get(UUID shopId, UUID id) {
        return toResponse(require(shopId, id));
    }

    private List<Payment> capture(UUID shopId, UUID purchaseId, List<PaymentRequest> requests) {
        if (requests == null) {
            return List.of();
        }
        List<Payment> saved = new ArrayList<>();
        for (PaymentRequest request : requests) {
            Payment payment = new Payment();
            payment.setShopId(shopId);
            payment.setReferenceType(PaymentReferenceType.PURCHASE);
            payment.setReferenceId(purchaseId);
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

    private Purchase require(UUID shopId, UUID id) {
        return purchaseRepository.findByIdAndShopId(id, shopId)
                .orElseThrow(() -> ApiException.notFound("Purchase", id));
    }

    private PurchaseResponse toResponse(Purchase purchase) {
        String supplierName = supplierRepository.findById(purchase.getSupplierId())
                .map(Supplier::getName).orElse(null);
        return toResponse(purchase, supplierName,
                purchaseItemRepository.findByPurchaseIdOrderByCreatedAtAsc(purchase.getId()),
                paymentRepository.findByShopIdAndReferenceTypeAndReferenceIdOrderByOccurredAtAsc(
                        purchase.getShopId(), PaymentReferenceType.PURCHASE, purchase.getId()));
    }

    private PurchaseResponse toResponse(Purchase purchase, String supplierName, List<PurchaseItem> items,
                                        List<Payment> payments) {
        return new PurchaseResponse(purchase.getId(), purchase.getSupplierId(), supplierName, purchase.getStatus(),
                purchase.getSubtotal(), purchase.getTax(), purchase.getTotal(), purchase.getPaid(),
                purchase.getOutstanding(), purchase.getNotes(), purchase.getReceivedAt(),
                items.stream().map(this::toItem).toList(),
                payments.stream().map(this::toPayment).toList());
    }

    private PurchaseItemResponse toItem(PurchaseItem item) {
        String name = variantRepository.findById(item.getProductVariantId())
                .map(ProductVariant::getVariantName).orElse(null);
        return new PurchaseItemResponse(item.getId(), item.getProductVariantId(), name, item.getQuantity(),
                item.getUnitCost(), item.getLineTotal(), item.getBatchNo());
    }

    private PaymentResponse toPayment(Payment payment) {
        return new PaymentResponse(payment.getId(), payment.getMethod(), payment.getAmount(),
                payment.getStatus(), payment.getOccurredAt(), payment.getNotes());
    }
}
