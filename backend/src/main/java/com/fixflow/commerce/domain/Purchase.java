package com.fixflow.commerce.domain;

import com.fixflow.common.domain.AuditableEntity;
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

@Getter
@Setter
@Entity
@Table(name = "purchases")
public class Purchase extends AuditableEntity {

    @Column(name = "shop_id", nullable = false)
    private UUID shopId;

    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PurchaseStatus status = PurchaseStatus.RECEIVED;

    @Column(name = "subtotal", nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "tax", nullable = false, precision = 14, scale = 2)
    private BigDecimal tax = BigDecimal.ZERO;

    @Column(name = "total", nullable = false, precision = 14, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "paid", nullable = false, precision = 14, scale = 2)
    private BigDecimal paid = BigDecimal.ZERO;

    @Column(name = "outstanding", nullable = false, precision = 14, scale = 2)
    private BigDecimal outstanding = BigDecimal.ZERO;

    @Column(name = "notes")
    private String notes;

    @Column(name = "idempotency_key", length = 80)
    private String idempotencyKey;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt = Instant.now();
}
