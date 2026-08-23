package com.fixflow.repair.service;

import com.fixflow.audit.service.AuditAction;
import com.fixflow.audit.service.AuditService;
import com.fixflow.catalog.domain.DeviceModel;
import com.fixflow.catalog.domain.ProductVariant;
import com.fixflow.catalog.repository.DeviceModelRepository;
import com.fixflow.catalog.repository.ProductVariantRepository;
import com.fixflow.commerce.domain.Payment;
import com.fixflow.commerce.domain.PaymentMethod;
import com.fixflow.commerce.domain.PaymentReferenceType;
import com.fixflow.commerce.domain.PaymentStatus;
import com.fixflow.commerce.dto.CommerceDtos.PaymentRequest;
import com.fixflow.commerce.dto.CommerceDtos.PaymentResponse;
import com.fixflow.commerce.repository.PaymentRepository;
import com.fixflow.common.error.ApiException;
import com.fixflow.inventory.domain.InventoryReferenceType;
import com.fixflow.inventory.domain.InventoryTransactionType;
import com.fixflow.inventory.service.InventoryService;
import com.fixflow.inventory.service.StockMovement;
import com.fixflow.party.domain.Customer;
import com.fixflow.party.repository.CustomerRepository;
import com.fixflow.party.service.PartyService;
import com.fixflow.pricing.domain.PricingFlag;
import com.fixflow.pricing.service.PriceContext;
import com.fixflow.pricing.service.PriceQuote;
import com.fixflow.pricing.service.PricingService;
import com.fixflow.repair.domain.RepairJob;
import com.fixflow.repair.domain.RepairPart;
import com.fixflow.repair.domain.RepairStatus;
import com.fixflow.repair.dto.RepairDtos.AddRepairPartRequest;
import com.fixflow.repair.dto.RepairDtos.CollectRepairPaymentRequest;
import com.fixflow.repair.dto.RepairDtos.CreateRepairRequest;
import com.fixflow.repair.dto.RepairDtos.RepairPartResponse;
import com.fixflow.repair.dto.RepairDtos.RepairResponse;
import com.fixflow.repair.dto.RepairDtos.UpdateRepairRequest;
import com.fixflow.repair.repository.RepairJobRepository;
import com.fixflow.repair.repository.RepairPartRepository;
import com.fixflow.shop.domain.Shop;
import com.fixflow.shop.repository.ShopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RepairService {

    private final RepairJobRepository repairRepository;
    private final RepairPartRepository repairPartRepository;
    private final PaymentRepository paymentRepository;
    private final CustomerRepository customerRepository;
    private final DeviceModelRepository deviceModelRepository;
    private final ProductVariantRepository variantRepository;
    private final ShopRepository shopRepository;
    private final PricingService pricingService;
    private final InventoryService inventoryService;
    private final AuditService auditService;

    @Transactional
    public RepairResponse create(UUID shopId, CreateRepairRequest request) {
        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            var existing = repairRepository.findByShopIdAndIdempotencyKey(shopId, request.idempotencyKey());
            if (existing.isPresent()) {
                return toResponse(existing.get());
            }
        }
        if (request.customerId() != null) {
            customerRepository.findByIdAndShopId(request.customerId(), shopId)
                    .orElseThrow(() -> ApiException.notFound("Customer", request.customerId()));
        }
        RepairJob job = new RepairJob();
        job.setShopId(shopId);
        job.setCustomerId(request.customerId());
        job.setDeviceModelId(request.deviceModelId());
        job.setTechnicianUserId(request.technicianUserId());
        job.setJobNumber(nextJobNumber(shopId));
        job.setImei(request.imei());
        job.setProblem(request.problem().trim());
        job.setStatus(RepairStatus.RECEIVED);
        job.setEstimatedCost(PartyService.nz(request.estimatedCost()));
        job.setLaborCharge(PartyService.nz(request.laborCharge()));
        job.setLaborCost(PartyService.nz(request.laborCost()));
        job.setCustomerNotes(request.customerNotes());
        job.setInternalNotes(request.internalNotes());
        job.setExpectedAt(request.expectedAt());
        job.setIdempotencyKey(request.idempotencyKey());
        recompute(job);
        repairRepository.save(job);
        auditService.record(AuditAction.REPAIR_CREATED, "Repair", job.getId(),
                "Opened repair %s".formatted(job.getJobNumber()));
        return toResponse(job);
    }

    @Transactional
    public RepairResponse update(UUID shopId, UUID id, UpdateRepairRequest request) {
        RepairJob job = require(shopId, id);
        RepairStatus previous = job.getStatus();
        if (request.status() != null) {
            if (request.status() == RepairStatus.CANCELLED && previous != RepairStatus.CANCELLED) {
                releaseParts(shopId, job);
            }
            job.setStatus(request.status());
            if (request.status() == RepairStatus.DELIVERED) {
                job.setDeliveredAt(Instant.now());
            }
        }
        if (request.technicianUserId() != null) {
            job.setTechnicianUserId(request.technicianUserId());
        }
        if (request.laborCharge() != null) {
            job.setLaborCharge(request.laborCharge());
        }
        if (request.laborCost() != null) {
            job.setLaborCost(request.laborCost());
        }
        if (request.customerNotes() != null) {
            job.setCustomerNotes(request.customerNotes());
        }
        if (request.internalNotes() != null) {
            job.setInternalNotes(request.internalNotes());
        }
        if (request.expectedAt() != null) {
            job.setExpectedAt(request.expectedAt());
        }
        recompute(job);
        repairRepository.save(job);
        if (request.status() != null && request.status() != previous) {
            auditService.record(AuditAction.REPAIR_STATUS_CHANGED, "Repair", id,
                    "Repair %s moved to %s".formatted(job.getJobNumber(), request.status()));
            if (request.status() == RepairStatus.DELIVERED) {
                auditService.record(AuditAction.REPAIR_DELIVERED, "Repair", id,
                        "Delivered repair %s".formatted(job.getJobNumber()));
            }
        } else {
            auditService.record(AuditAction.REPAIR_UPDATED, "Repair", id,
                    "Updated repair %s".formatted(job.getJobNumber()));
        }
        return toResponse(job);
    }

    @Transactional
    public RepairResponse addPart(UUID shopId, UUID repairId, AddRepairPartRequest request) {
        RepairJob job = require(shopId, repairId);
        if (job.getStatus() == RepairStatus.DELIVERED || job.getStatus() == RepairStatus.CANCELLED) {
            throw ApiException.businessRule("Cannot add parts to a closed repair.");
        }
        ProductVariant variant = variantRepository.findByIdAndShopId(request.variantId(), shopId)
                .orElseThrow(() -> ApiException.notFound("Product variant", request.variantId()));
        PriceQuote quote = pricingService.quote(PriceContext.of(shopId, variant, PricingFlag.REPAIR)
                .withQuantity(request.quantity()));
        BigDecimal unitPrice = request.unitPrice() == null ? quote.unitPrice() : request.unitPrice();
        pricingService.assertAboveMinimum(variant, unitPrice);

        RepairPart part = new RepairPart();
        part.setShopId(shopId);
        part.setRepairId(job.getId());
        part.setProductVariantId(variant.getId());
        part.setQuantity(request.quantity());
        part.setUnitPrice(unitPrice);
        part.setUnitCost(quote.costPrice());
        part.setLineTotal(unitPrice.multiply(BigDecimal.valueOf(request.quantity())));
        repairPartRepository.save(part);

        inventoryService.post(shopId, StockMovement.of(variant.getId(), InventoryTransactionType.OUT, request.quantity())
                .reference(InventoryReferenceType.REPAIR, job.getId(), job.getJobNumber())
                .idempotencyKey(job.getId() + ":part:" + part.getId())
                .build());

        if (job.getStatus() == RepairStatus.RECEIVED || job.getStatus() == RepairStatus.DIAGNOSING) {
            job.setStatus(RepairStatus.IN_REPAIR);
        }
        recompute(job);
        repairRepository.save(job);
        return toResponse(job);
    }

    @Transactional
    public RepairResponse collect(UUID shopId, UUID repairId, CollectRepairPaymentRequest request) {
        RepairJob job = require(shopId, repairId);
        if (request.payments() != null) {
            for (PaymentRequest line : request.payments()) {
                Payment payment = new Payment();
                payment.setShopId(shopId);
                payment.setReferenceType(PaymentReferenceType.REPAIR);
                payment.setReferenceId(job.getId());
                payment.setMethod(line.method() == null ? PaymentMethod.CASH : line.method());
                payment.setAmount(line.amount());
                payment.setStatus(PaymentStatus.CAPTURED);
                payment.setOccurredAt(Instant.now());
                paymentRepository.save(payment);
            }
        }
        BigDecimal paid = paymentsOf(job).stream().map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        job.setPaid(paid.min(job.getTotal()));
        job.setOutstanding(job.getTotal().subtract(job.getPaid()).max(BigDecimal.ZERO));
        if (job.getCustomerId() != null) {
            customerRepository.findByIdAndShopId(job.getCustomerId(), shopId).ifPresent(customer -> {
                customer.setOutstandingAmount(PartyService.nz(customer.getOutstandingAmount()).add(job.getOutstanding()));
                customer.setLastTransactionAt(Instant.now());
                customerRepository.save(customer);
            });
        }
        repairRepository.save(job);
        return toResponse(job);
    }

    @Transactional(readOnly = true)
    public Page<RepairResponse> list(UUID shopId, RepairStatus status, Pageable pageable) {
        Page<RepairJob> page = status == null
                ? repairRepository.findByShopIdOrderByCreatedAtDesc(shopId, pageable)
                : repairRepository.findByShopIdAndStatusOrderByCreatedAtDesc(shopId, status, pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public RepairResponse get(UUID shopId, UUID id) {
        return toResponse(require(shopId, id));
    }

    private void releaseParts(UUID shopId, RepairJob job) {
        for (RepairPart part : repairPartRepository.findByRepairIdOrderByCreatedAtAsc(job.getId())) {
            inventoryService.post(shopId, StockMovement.of(part.getProductVariantId(),
                            InventoryTransactionType.RETURN, part.getQuantity())
                    .reference(InventoryReferenceType.REPAIR, job.getId(), job.getJobNumber())
                    .reason("Repair cancelled")
                    .build());
        }
    }

    private void recompute(RepairJob job) {
        List<RepairPart> parts = job.getId() == null ? List.of()
                : repairPartRepository.findByRepairIdOrderByCreatedAtAsc(job.getId());
        BigDecimal partsTotal = parts.stream().map(RepairPart::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal partsCost = parts.stream()
                .map(part -> part.getUnitCost().multiply(BigDecimal.valueOf(part.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        job.setPartsTotal(partsTotal);
        job.setPartsCost(partsCost);
        job.setTotal(partsTotal.add(PartyService.nz(job.getLaborCharge())));
        BigDecimal paid = paymentsOf(job).stream().map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        job.setPaid(paid.min(job.getTotal()));
        job.setOutstanding(job.getTotal().subtract(job.getPaid()).max(BigDecimal.ZERO));
        job.setProfit(job.getTotal().subtract(partsCost).subtract(PartyService.nz(job.getLaborCost())));
    }

    private List<Payment> paymentsOf(RepairJob job) {
        if (job.getId() == null) {
            return List.of();
        }
        return paymentRepository.findByShopIdAndReferenceTypeAndReferenceIdOrderByOccurredAtAsc(
                job.getShopId(), PaymentReferenceType.REPAIR, job.getId());
    }

    private String nextJobNumber(UUID shopId) {
        Shop shop = shopRepository.findByIdForUpdate(shopId)
                .orElseThrow(() -> ApiException.notFound("Shop", shopId));
        long next = shop.getRepairNextNumber();
        shop.setRepairNextNumber(next + 1);
        shopRepository.save(shop);
        return shop.getRepairPrefix() + String.format("%06d", next);
    }

    private RepairJob require(UUID shopId, UUID id) {
        return repairRepository.findByIdAndShopId(id, shopId)
                .orElseThrow(() -> ApiException.notFound("Repair", id));
    }

    private RepairResponse toResponse(RepairJob job) {
        List<RepairPart> parts = repairPartRepository.findByRepairIdOrderByCreatedAtAsc(job.getId());
        List<Payment> payments = paymentsOf(job);
        String customerName = job.getCustomerId() == null ? null
                : customerRepository.findById(job.getCustomerId()).map(Customer::getName).orElse(null);
        String deviceName = job.getDeviceModelId() == null ? null
                : deviceModelRepository.findById(job.getDeviceModelId()).map(DeviceModel::getName).orElse(null);
        return new RepairResponse(job.getId(), job.getJobNumber(), job.getStatus(), job.getCustomerId(), customerName,
                job.getDeviceModelId(), deviceName, job.getTechnicianUserId(), job.getProblem(), job.getImei(),
                job.getEstimatedCost(), job.getLaborCharge(), job.getLaborCost(), job.getPartsTotal(),
                job.getPartsCost(), job.getTotal(), job.getPaid(), job.getOutstanding(), job.getProfit(),
                job.getCustomerNotes(), job.getInternalNotes(), job.getExpectedAt(), job.getDeliveredAt(),
                job.getCreatedAt(), parts.stream().map(this::toPart).toList(),
                payments.stream().map(this::toPayment).toList());
    }

    private RepairPartResponse toPart(RepairPart part) {
        String name = variantRepository.findById(part.getProductVariantId())
                .map(ProductVariant::getVariantName).orElse(null);
        return new RepairPartResponse(part.getId(), part.getProductVariantId(), name, part.getQuantity(),
                part.getUnitPrice(), part.getUnitCost(), part.getLineTotal());
    }

    private PaymentResponse toPayment(Payment payment) {
        return new PaymentResponse(payment.getId(), payment.getMethod(), payment.getAmount(),
                payment.getStatus(), payment.getOccurredAt(), payment.getNotes());
    }
}
