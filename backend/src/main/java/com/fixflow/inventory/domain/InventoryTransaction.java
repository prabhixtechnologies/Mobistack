package com.fixflow.inventory.domain;

import com.fixflow.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Append-only stock ledger. Nothing here is ever updated or deleted: a mistake
 * is corrected by posting a compensating row, which keeps the movement history
 * complete and auditable.
 */
@Getter
@Setter
@Entity
@Table(name = "inventory_transactions")
public class InventoryTransaction extends BaseEntity {

    @Column(name = "shop_id", nullable = false)
    private UUID shopId;

    @Column(name = "product_variant_id", nullable = false)
    private UUID productVariantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private InventoryTransactionType type;

    /** Always positive; direction lives in the deltas below. */
    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "on_hand_delta", nullable = false)
    private int onHandDelta;

    @Column(name = "reserved_delta", nullable = false)
    private int reservedDelta;

    @Column(name = "balance_after", nullable = false)
    private int balanceAfter;

    @Column(name = "unit_cost", precision = 14, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "total_cost", precision = 14, scale = 2)
    private BigDecimal totalCost;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", nullable = false, length = 24)
    private InventoryReferenceType referenceType = InventoryReferenceType.MANUAL;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "reference_label", length = 80)
    private String referenceLabel;

    /**
     * Supplied by offline clients. A replayed sync with the same key is
     * recognised and ignored rather than double-posting stock.
     */
    @Column(name = "idempotency_key", length = 120)
    private String idempotencyKey;

    @Column(name = "device_id", length = 120)
    private String deviceId;

    @Column(name = "batch_no", length = 64)
    private String batchNo;

    @Column(name = "serial_no", length = 120)
    private String serialNo;

    @Column(name = "reason", length = 160)
    private String reason;

    @Column(name = "notes")
    private String notes;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();

    @Column(name = "created_by_name", length = 160)
    private String createdByName;
}
