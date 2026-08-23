package com.fixflow.inventory.service;

import com.fixflow.audit.service.AuditAction;
import com.fixflow.audit.service.AuditService;
import com.fixflow.catalog.domain.ProductVariant;
import com.fixflow.catalog.domain.StockStatus;
import com.fixflow.catalog.repository.ProductVariantRepository;
import com.fixflow.common.error.ApiException;
import com.fixflow.common.error.ErrorCode;
import com.fixflow.config.FixFlowProperties;
import com.fixflow.inventory.domain.InventoryReferenceType;
import com.fixflow.inventory.domain.InventoryTransaction;
import com.fixflow.inventory.domain.InventoryTransactionType;
import com.fixflow.inventory.domain.StockAlert;
import com.fixflow.inventory.repository.InventoryTransactionRepository;
import com.fixflow.inventory.repository.StockAlertRepository;
import com.fixflow.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The only way stock ever moves.
 *
 * <p>Each call appends a row to {@code inventory_transactions} and updates the
 * cached quantities on the variant inside the same database transaction, so the
 * ledger and the cache can never drift. The variant row is locked first, which
 * is what stops two concurrent sales from both taking the last unit.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final ProductVariantRepository variantRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final StockAlertRepository stockAlertRepository;
    private final AuditService auditService;
    private final FixFlowProperties properties;
    private final com.fixflow.billing.service.BillingService billingService;

    @Transactional
    public InventoryTransaction post(UUID shopId, StockMovement movement) {
        billingService.require(shopId, "INVENTORY");
        if (movement.quantity() <= 0) {
            throw ApiException.businessRule("Quantity must be greater than zero.");
        }

        // Replaying a sync from an offline device must not double-post stock.
        if (movement.idempotencyKey() != null) {
            Optional<InventoryTransaction> existing = transactionRepository
                    .findByShopIdAndIdempotencyKey(shopId, movement.idempotencyKey());
            if (existing.isPresent()) {
                log.debug("Ignoring replayed stock movement {}", movement.idempotencyKey());
                return existing.get();
            }
        }

        ProductVariant variant = variantRepository.findForStockUpdate(movement.variantId(), shopId)
                .orElseThrow(() -> ApiException.notFound("Product variant", movement.variantId()));

        int onHandDelta = resolveOnHandDelta(movement);
        int reservedDelta = resolveReservedDelta(movement);

        int newOnHand = variant.getOnHandQty() + onHandDelta;
        int newReserved = variant.getReservedQty() + reservedDelta;

        if (newOnHand < 0) {
            throw new ApiException(ErrorCode.INSUFFICIENT_STOCK,
                    "%s has only %d in stock; cannot remove %d."
                            .formatted(variant.getVariantName(), variant.getOnHandQty(), movement.quantity()),
                    Map.of("available", variant.available(), "requested", movement.quantity()));
        }
        if (newReserved < 0) {
            newReserved = 0;
        }
        if (newReserved > newOnHand) {
            throw new ApiException(ErrorCode.INSUFFICIENT_STOCK,
                    "Cannot reserve %d of %s; only %d on hand."
                            .formatted(movement.quantity(), variant.getVariantName(), newOnHand),
                    Map.of("available", newOnHand - variant.getReservedQty()));
        }

        InventoryTransaction transaction = new InventoryTransaction();
        transaction.setShopId(shopId);
        transaction.setProductVariantId(variant.getId());
        transaction.setType(movement.type());
        transaction.setQuantity(Math.abs(movement.quantity()));
        transaction.setOnHandDelta(onHandDelta);
        transaction.setReservedDelta(reservedDelta);
        transaction.setBalanceAfter(newOnHand);
        transaction.setUnitCost(movement.unitCost());
        transaction.setTotalCost(movement.unitCost() == null ? null
                : movement.unitCost().multiply(BigDecimal.valueOf(movement.quantity())));
        transaction.setReferenceType(movement.referenceType());
        transaction.setReferenceId(movement.referenceId());
        transaction.setReferenceLabel(movement.referenceLabel());
        transaction.setIdempotencyKey(movement.idempotencyKey());
        transaction.setDeviceId(movement.deviceId());
        transaction.setBatchNo(movement.batchNo());
        transaction.setSerialNo(movement.serialNo());
        transaction.setReason(movement.reason());
        transaction.setNotes(movement.notes());
        transaction.setOccurredAt(movement.occurredAt());
        transaction.setCreatedByName(CurrentUser.find().map(p -> p.getFullName()).orElse("System"));
        transactionRepository.save(transaction);

        variant.setOnHandQty(newOnHand);
        variant.setReservedQty(newReserved);
        applyMovementTimestamps(variant, movement);

        // Receiving at a new cost keeps the costing basis current for margins.
        if (movement.type().increasesStock() && movement.unitCost() != null
                && movement.unitCost().signum() > 0) {
            variant.setCostPrice(movement.unitCost());
        }

        variantRepository.save(variant);
        refreshAlerts(variant);
        writeAudit(variant, movement, newOnHand);

        return transaction;
    }

    // -----------------------------------------------------------------
    // Convenience operations
    // -----------------------------------------------------------------

    @Transactional
    public InventoryTransaction receive(UUID shopId, UUID variantId, int quantity, BigDecimal unitCost,
                                        String reason, String batchNo) {
        return post(shopId, StockMovement.of(variantId, InventoryTransactionType.IN, quantity)
                .unitCost(unitCost)
                .reason(reason)
                .batchNo(batchNo)
                .build());
    }

    @Transactional
    public InventoryTransaction issue(UUID shopId, UUID variantId, int quantity,
                                      InventoryReferenceType referenceType, UUID referenceId, String label) {
        return post(shopId, StockMovement.of(variantId, InventoryTransactionType.OUT, quantity)
                .reference(referenceType, referenceId, label)
                .build());
    }

    /**
     * Stock-take correction. The caller states the counted quantity and the
     * service works out the signed difference, so the ledger records what
     * actually changed rather than an absolute overwrite.
     */
    @Transactional
    public InventoryTransaction adjustTo(UUID shopId, UUID variantId, int countedQuantity, String reason) {
        ProductVariant variant = variantRepository.findByIdAndShopId(variantId, shopId)
                .orElseThrow(() -> ApiException.notFound("Product variant", variantId));
        int delta = countedQuantity - variant.getOnHandQty();
        if (delta == 0) {
            throw ApiException.businessRule("Counted quantity already matches the recorded stock.");
        }
        return post(shopId, StockMovement.of(variantId, InventoryTransactionType.ADJUSTMENT, Math.abs(delta))
                .signedDelta(delta)
                .reference(InventoryReferenceType.STOCK_TAKE, null, null)
                .reason(reason)
                .build());
    }

    @Transactional
    public InventoryTransaction reserve(UUID shopId, UUID variantId, int quantity,
                                        UUID referenceId, String label) {
        return post(shopId, StockMovement.of(variantId, InventoryTransactionType.RESERVATION, quantity)
                .reference(InventoryReferenceType.REPAIR, referenceId, label)
                .build());
    }

    @Transactional
    public InventoryTransaction release(UUID shopId, UUID variantId, int quantity,
                                        UUID referenceId, String label) {
        return post(shopId, StockMovement.of(variantId, InventoryTransactionType.RELEASE, quantity)
                .reference(InventoryReferenceType.REPAIR, referenceId, label)
                .build());
    }

    /**
     * Rebuilds the cached quantities from the ledger and reports any drift.
     * The ledger is authoritative; the cache exists only for query speed.
     */
    @Transactional
    public StockReconciliation reconcile(UUID shopId, UUID variantId) {
        ProductVariant variant = variantRepository.findForStockUpdate(variantId, shopId)
                .orElseThrow(() -> ApiException.notFound("Product variant", variantId));

        int ledgerOnHand = transactionRepository.sumOnHandDelta(variantId);
        int ledgerReserved = Math.max(0, transactionRepository.sumReservedDelta(variantId));
        int cachedOnHand = variant.getOnHandQty();
        int cachedReserved = variant.getReservedQty();

        if (cachedOnHand != ledgerOnHand || cachedReserved != ledgerReserved) {
            log.warn("Stock drift on variant {}: cached {}/{} vs ledger {}/{}",
                    variantId, cachedOnHand, cachedReserved, ledgerOnHand, ledgerReserved);
            variant.setOnHandQty(Math.max(0, ledgerOnHand));
            variant.setReservedQty(ledgerReserved);
            variantRepository.save(variant);
            refreshAlerts(variant);
        }

        return new StockReconciliation(variantId, cachedOnHand, ledgerOnHand, cachedReserved, ledgerReserved,
                cachedOnHand != ledgerOnHand || cachedReserved != ledgerReserved);
    }

    public record StockReconciliation(UUID variantId, int cachedOnHand, int ledgerOnHand,
                                      int cachedReserved, int ledgerReserved, boolean corrected) {
    }

    // -----------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------

    private int resolveOnHandDelta(StockMovement movement) {
        if (movement.type().hasFixedDirection()) {
            return movement.type().onHandDelta(movement.quantity());
        }
        if (movement.signedDelta() == null) {
            throw ApiException.businessRule(
                    movement.type() + " requires an explicit signed quantity.");
        }
        return movement.signedDelta();
    }

    private int resolveReservedDelta(StockMovement movement) {
        return movement.type().reservedDelta(movement.quantity());
    }

    private void applyMovementTimestamps(ProductVariant variant, StockMovement movement) {
        Instant when = movement.occurredAt();
        if (movement.type().increasesStock()) {
            if (variant.getFirstStockedAt() == null) {
                variant.setFirstStockedAt(when);
            }
            if (movement.referenceType() == InventoryReferenceType.PURCHASE
                    || movement.referenceType() == InventoryReferenceType.MANUAL) {
                variant.setLastPurchasedAt(when);
            }
        }
        if (movement.type() == InventoryTransactionType.OUT
                && (movement.referenceType() == InventoryReferenceType.SALE
                || movement.referenceType() == InventoryReferenceType.REPAIR)) {
            variant.setLastSoldAt(when);
        }
    }

    /**
     * Keeps at most one open alert per variant per type: raise it when stock
     * crosses a threshold, resolve it when stock recovers.
     */
    private void refreshAlerts(ProductVariant variant) {
        StockStatus status = variant.stockStatus(properties.getInventory().getCriticalStockFactor());
        int available = variant.available();

        resolveIfOpen(variant, StockAlert.AlertType.OUT_OF_STOCK, available > 0);
        resolveIfOpen(variant, StockAlert.AlertType.LOW_STOCK,
                status == StockStatus.GREEN || available <= 0);

        if (available <= 0) {
            raise(variant, StockAlert.AlertType.OUT_OF_STOCK, StockStatus.RED,
                    "%s is out of stock.".formatted(variant.getVariantName()), available);
        } else if (status != StockStatus.GREEN) {
            raise(variant, StockAlert.AlertType.LOW_STOCK, status,
                    "%s is down to %d (reorder at %d)."
                            .formatted(variant.getVariantName(), available, variant.getReorderLevel()),
                    available);
        }
    }

    private void raise(ProductVariant variant, StockAlert.AlertType type, StockStatus severity,
                       String message, int observed) {
        StockAlert alert = stockAlertRepository.findOpen(variant.getId(), type).orElseGet(StockAlert::new);
        alert.setShopId(variant.getShopId());
        alert.setProductVariantId(variant.getId());
        alert.setAlertType(type);
        alert.setSeverity(severity);
        alert.setStatus(alert.getId() == null ? StockAlert.Status.OPEN : alert.getStatus());
        alert.setThresholdValue(variant.getReorderLevel());
        alert.setObservedValue(observed);
        alert.setMessage(message);
        alert.setUpdatedAt(Instant.now());
        stockAlertRepository.save(alert);
    }

    private void resolveIfOpen(ProductVariant variant, StockAlert.AlertType type, boolean shouldResolve) {
        if (!shouldResolve) {
            return;
        }
        stockAlertRepository.findOpen(variant.getId(), type).ifPresent(alert -> {
            alert.setStatus(StockAlert.Status.RESOLVED);
            alert.setResolvedAt(Instant.now());
            alert.setUpdatedAt(Instant.now());
            stockAlertRepository.save(alert);
        });
    }

    private void writeAudit(ProductVariant variant, StockMovement movement, int newBalance) {
        AuditAction action = switch (movement.type()) {
            case IN, OPENING -> AuditAction.STOCK_RECEIVED;
            case OUT -> AuditAction.STOCK_ISSUED;
            case RETURN -> AuditAction.STOCK_RETURNED;
            case DAMAGE -> AuditAction.STOCK_DAMAGED;
            case RESERVATION -> AuditAction.STOCK_RESERVED;
            case RELEASE -> AuditAction.STOCK_RELEASED;
            case ADJUSTMENT, TRANSFER -> AuditAction.STOCK_ADJUSTED;
        };
        auditService.record(action, "ProductVariant", variant.getId(),
                "%s %s x%d — stock now %d".formatted(movement.type(), variant.getVariantName(),
                        movement.quantity(), newBalance));
    }
}
