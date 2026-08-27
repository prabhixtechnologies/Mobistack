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
import com.fixflow.notify.WorkspaceNotifier;
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
import com.fixflow.security.Permission;
import com.fixflow.shop.domain.Shop;
import com.fixflow.shop.repository.ShopRepository;
import com.fixflow.workspace.domain.MembershipStatus;
import com.fixflow.workspace.repository.WorkspaceMembershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final com.fixflow.billing.service.BillingService billingService;
    private final WorkspaceMembershipRepository membershipRepository;
    private final WorkspaceNotifier notifier;

    @Transactional
    public RepairResponse create(UUID shopId, CreateRepairRequest request) {
        billingService.require(shopId, "REPAIRS");
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
        requireOwnDevice(shopId, request.deviceModelId());
        requireTechnicianOfShop(shopId, request.technicianUserId());
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
            requireTechnicianOfShop(shopId, request.technicianUserId());
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
            announceStatus(shopId, job, request.status());
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

    /** Batched: one screen of jobs used to cost five queries per job plus one per part. */
    @Transactional(readOnly = true)
    public Page<RepairResponse> list(UUID shopId, RepairStatus status, Pageable pageable) {
        Page<RepairJob> page = status == null
                ? repairRepository.findByShopIdOrderByCreatedAtDesc(shopId, pageable)
                : repairRepository.findByShopIdAndStatusOrderByCreatedAtDesc(shopId, status, pageable);
        if (page.isEmpty()) {
            return page.map(job -> toResponse(job, List.of(), List.of(), Map.of(), Map.of(), Map.of()));
        }
        List<UUID> jobIds = page.getContent().stream().map(RepairJob::getId).toList();

        Map<UUID, List<RepairPart>> partsByJob = repairPartRepository.findByRepairIdInOrderByCreatedAtAsc(jobIds)
                .stream().collect(Collectors.groupingBy(RepairPart::getRepairId));
        Map<UUID, List<Payment>> paymentsByJob = paymentRepository
                .findByShopIdAndReferenceTypeAndReferenceIdInOrderByOccurredAtAsc(
                        shopId, PaymentReferenceType.REPAIR, jobIds)
                .stream().collect(Collectors.groupingBy(Payment::getReferenceId));
        Map<UUID, String> variantNames = variantNames(shopId, partsByJob.values().stream()
                .flatMap(List::stream).map(RepairPart::getProductVariantId).toList());
        Map<UUID, String> customerNames = customerNames(shopId, page.getContent().stream()
                .map(RepairJob::getCustomerId).toList());
        Map<UUID, String> deviceNames = deviceNames(shopId, page.getContent().stream()
                .map(RepairJob::getDeviceModelId).toList());

        return page.map(job -> toResponse(job,
                partsByJob.getOrDefault(job.getId(), List.of()),
                paymentsByJob.getOrDefault(job.getId(), List.of()),
                variantNames, customerNames, deviceNames));
    }

    /** All three scoped to the shop, so a stale id cannot pull a name from elsewhere. */
    private Map<UUID, String> variantNames(UUID shopId, Collection<UUID> ids) {
        List<UUID> wanted = distinct(ids);
        return wanted.isEmpty() ? Map.of()
                : variantRepository.findByShopIdAndIdIn(shopId, wanted).stream()
                .collect(Collectors.toMap(ProductVariant::getId, ProductVariant::getVariantName));
    }

    private Map<UUID, String> customerNames(UUID shopId, Collection<UUID> ids) {
        List<UUID> wanted = distinct(ids);
        return wanted.isEmpty() ? Map.of()
                : customerRepository.findByShopIdAndIdIn(shopId, wanted).stream()
                .collect(Collectors.toMap(Customer::getId, Customer::getName));
    }

    private Map<UUID, String> deviceNames(UUID shopId, Collection<UUID> ids) {
        List<UUID> wanted = distinct(ids);
        return wanted.isEmpty() ? Map.of()
                : deviceModelRepository.findByShopIdAndIdIn(shopId, wanted).stream()
                .collect(Collectors.toMap(DeviceModel::getId, DeviceModel::getName));
    }

    private static List<UUID> distinct(Collection<UUID> ids) {
        return ids.stream().filter(Objects::nonNull).distinct().toList();
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

    /**
     * A device model id arrives from the client, so it has to be checked before it
     * is stored. Without this a job can point at another shop's model and the
     * repair card then shows a device name the shop never created.
     */
    private void requireOwnDevice(UUID shopId, UUID deviceModelId) {
        if (deviceModelId == null) {
            return;
        }
        deviceModelRepository.findByIdAndShopId(deviceModelId, shopId)
                .orElseThrow(() -> ApiException.notFound("Device model", deviceModelId));
    }

    /** Work can only be assigned to somebody who actually works at this shop. */
    private void requireTechnicianOfShop(UUID shopId, UUID technicianUserId) {
        if (technicianUserId == null) {
            return;
        }
        boolean member = membershipRepository
                .findByWorkspaceIdAndUserIdAndStatus(shopId, technicianUserId, MembershipStatus.ACTIVE)
                .isPresent();
        if (!member) {
            throw ApiException.businessRule("That technician is not an active member of this shop.");
        }
    }

    /**
     * Alerts the counter when a job reaches a state somebody has to act on. In
     * a shop the technician and the person at the counter are rarely the same
     * person, so "ready" has to travel between them without a phone call.
     */
    private void announceStatus(UUID shopId, RepairJob job, RepairStatus status) {
        String customerName = job.getCustomerId() == null ? null
                : customerRepository.findByIdAndShopId(job.getCustomerId(), shopId)
                .map(Customer::getName).orElse(null);
        String who = customerName == null ? "Walk-in" : customerName;
        switch (status) {
            case READY -> notifier.broadcast(shopId, Permission.REPAIR_READ, "REPAIR_READY",
                    "Repair %s is ready".formatted(job.getJobNumber()),
                    "%s can be collected. Outstanding: %s.".formatted(who, job.getOutstanding()),
                    "/repairs");
            case WAITING_FOR_PART -> notifier.broadcast(shopId, Permission.REPAIR_READ, "REPAIR_READY",
                    "Repair %s is waiting on parts".formatted(job.getJobNumber()),
                    "%s cannot progress until the part arrives.".formatted(who),
                    "/repairs");
            default -> {
                // Other transitions are routine and already in the audit trail.
            }
        }
    }

    private RepairResponse toResponse(RepairJob job) {
        UUID shopId = job.getShopId();
        List<RepairPart> parts = repairPartRepository.findByRepairIdOrderByCreatedAtAsc(job.getId());
        return toResponse(job, parts, paymentsOf(job),
                variantNames(shopId, parts.stream().map(RepairPart::getProductVariantId).toList()),
                customerNames(shopId, Collections.singletonList(job.getCustomerId())),
                deviceNames(shopId, Collections.singletonList(job.getDeviceModelId())));
    }

    private RepairResponse toResponse(RepairJob job, List<RepairPart> parts, List<Payment> payments,
                                      Map<UUID, String> variantNames, Map<UUID, String> customerNames,
                                      Map<UUID, String> deviceNames) {
        String customerName = job.getCustomerId() == null ? null : customerNames.get(job.getCustomerId());
        String deviceName = job.getDeviceModelId() == null ? null : deviceNames.get(job.getDeviceModelId());
        return new RepairResponse(job.getId(), job.getJobNumber(), job.getStatus(), job.getCustomerId(), customerName,
                job.getDeviceModelId(), deviceName, job.getTechnicianUserId(), job.getProblem(), job.getImei(),
                job.getEstimatedCost(), job.getLaborCharge(), job.getLaborCost(), job.getPartsTotal(),
                job.getPartsCost(), job.getTotal(), job.getPaid(), job.getOutstanding(), job.getProfit(),
                job.getCustomerNotes(), job.getInternalNotes(), job.getExpectedAt(), job.getDeliveredAt(),
                job.getCreatedAt(), parts.stream().map(part -> toPart(part, variantNames)).toList(),
                payments.stream().map(this::toPayment).toList());
    }

    private RepairPartResponse toPart(RepairPart part, Map<UUID, String> variantNames) {
        return new RepairPartResponse(part.getId(), part.getProductVariantId(),
                variantNames.get(part.getProductVariantId()), part.getQuantity(),
                part.getUnitPrice(), part.getUnitCost(), part.getLineTotal());
    }

    private PaymentResponse toPayment(Payment payment) {
        return new PaymentResponse(payment.getId(), payment.getMethod(), payment.getAmount(),
                payment.getStatus(), payment.getOccurredAt(), payment.getNotes());
    }
}
