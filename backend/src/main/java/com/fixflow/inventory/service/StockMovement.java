package com.fixflow.inventory.service;

import com.fixflow.inventory.domain.InventoryReferenceType;
import com.fixflow.inventory.domain.InventoryTransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One requested stock change. Built with the fluent helpers below so call sites
 * stay readable and only ever set the fields that matter to them.
 */
public record StockMovement(
        UUID variantId,
        InventoryTransactionType type,
        int quantity,
        /* Direction for ADJUSTMENT/TRANSFER, which have no inherent sign. */
        Integer signedDelta,
        BigDecimal unitCost,
        InventoryReferenceType referenceType,
        UUID referenceId,
        String referenceLabel,
        String idempotencyKey,
        String deviceId,
        String batchNo,
        String serialNo,
        String reason,
        String notes,
        Instant occurredAt
) {

    public static Builder of(UUID variantId, InventoryTransactionType type, int quantity) {
        return new Builder(variantId, type, quantity);
    }

    public static final class Builder {
        private final UUID variantId;
        private final InventoryTransactionType type;
        private final int quantity;
        private Integer signedDelta;
        private BigDecimal unitCost;
        private InventoryReferenceType referenceType = InventoryReferenceType.MANUAL;
        private UUID referenceId;
        private String referenceLabel;
        private String idempotencyKey;
        private String deviceId;
        private String batchNo;
        private String serialNo;
        private String reason;
        private String notes;
        private Instant occurredAt;

        private Builder(UUID variantId, InventoryTransactionType type, int quantity) {
            this.variantId = variantId;
            this.type = type;
            this.quantity = quantity;
        }

        public Builder signedDelta(Integer value) {
            this.signedDelta = value;
            return this;
        }

        public Builder unitCost(BigDecimal value) {
            this.unitCost = value;
            return this;
        }

        public Builder reference(InventoryReferenceType refType, UUID refId, String label) {
            this.referenceType = refType;
            this.referenceId = refId;
            this.referenceLabel = label;
            return this;
        }

        public Builder idempotencyKey(String value) {
            this.idempotencyKey = value;
            return this;
        }

        public Builder deviceId(String value) {
            this.deviceId = value;
            return this;
        }

        public Builder batchNo(String value) {
            this.batchNo = value;
            return this;
        }

        public Builder serialNo(String value) {
            this.serialNo = value;
            return this;
        }

        public Builder reason(String value) {
            this.reason = value;
            return this;
        }

        public Builder notes(String value) {
            this.notes = value;
            return this;
        }

        public Builder occurredAt(Instant value) {
            this.occurredAt = value;
            return this;
        }

        public StockMovement build() {
            return new StockMovement(variantId, type, quantity, signedDelta, unitCost, referenceType,
                    referenceId, referenceLabel, idempotencyKey, deviceId, batchNo, serialNo, reason,
                    notes, occurredAt == null ? Instant.now() : occurredAt);
        }
    }
}
