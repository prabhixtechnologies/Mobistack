package com.fixflow.commerce.domain;

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

@Getter
@Setter
@Entity
@Table(name = "payments")
public class Payment extends BaseEntity {

    @Column(name = "shop_id", nullable = false)
    private UUID shopId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", nullable = false, length = 20)
    private PaymentReferenceType referenceType;

    @Column(name = "reference_id", nullable = false)
    private UUID referenceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 20)
    private PaymentMethod method;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private PaymentStatus status = PaymentStatus.CAPTURED;

    @Column(name = "gateway", length = 40)
    private String gateway;

    @Column(name = "gateway_ref", length = 80)
    private String gatewayRef;

    @Column(name = "notes", length = 255)
    private String notes;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();
}
