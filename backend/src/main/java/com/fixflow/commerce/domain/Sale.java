package com.fixflow.commerce.domain;

import com.fixflow.common.domain.AuditableEntity;
import com.fixflow.pricing.domain.PricingFlag;
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
@Table(name = "sales")
public class Sale extends AuditableEntity {

    @Column(name = "shop_id", nullable = false)
    private UUID shopId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "invoice_number", nullable = false, length = 32)
    private String invoiceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SaleStatus status = SaleStatus.COMPLETED;

    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_flag", nullable = false, length = 20)
    private PricingFlag pricingFlag = PricingFlag.NORMAL;

    @Column(name = "subtotal", nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "discount", nullable = false, precision = 14, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(name = "tax", nullable = false, precision = 14, scale = 2)
    private BigDecimal tax = BigDecimal.ZERO;

    @Column(name = "total", nullable = false, precision = 14, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "paid", nullable = false, precision = 14, scale = 2)
    private BigDecimal paid = BigDecimal.ZERO;

    @Column(name = "outstanding", nullable = false, precision = 14, scale = 2)
    private BigDecimal outstanding = BigDecimal.ZERO;

    @Column(name = "profit", nullable = false, precision = 14, scale = 2)
    private BigDecimal profit = BigDecimal.ZERO;

    @Column(name = "notes")
    private String notes;

    @Column(name = "idempotency_key", length = 80)
    private String idempotencyKey;

    @Column(name = "device_id", length = 80)
    private String deviceId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "void_reason", length = 255)
    private String voidReason;
}
