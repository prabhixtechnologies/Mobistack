package com.fixflow.repair.domain;

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
@Table(name = "repairs")
public class RepairJob extends AuditableEntity {

    @Column(name = "shop_id", nullable = false)
    private UUID shopId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "device_model_id")
    private UUID deviceModelId;

    @Column(name = "technician_user_id")
    private UUID technicianUserId;

    @Column(name = "job_number", nullable = false, length = 32)
    private String jobNumber;

    @Column(name = "imei", length = 32)
    private String imei;

    @Column(name = "problem", nullable = false)
    private String problem;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private RepairStatus status = RepairStatus.RECEIVED;

    @Column(name = "estimated_cost", nullable = false, precision = 14, scale = 2)
    private BigDecimal estimatedCost = BigDecimal.ZERO;

    @Column(name = "labor_charge", nullable = false, precision = 14, scale = 2)
    private BigDecimal laborCharge = BigDecimal.ZERO;

    @Column(name = "labor_cost", nullable = false, precision = 14, scale = 2)
    private BigDecimal laborCost = BigDecimal.ZERO;

    @Column(name = "parts_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal partsTotal = BigDecimal.ZERO;

    @Column(name = "parts_cost", nullable = false, precision = 14, scale = 2)
    private BigDecimal partsCost = BigDecimal.ZERO;

    @Column(name = "total", nullable = false, precision = 14, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "paid", nullable = false, precision = 14, scale = 2)
    private BigDecimal paid = BigDecimal.ZERO;

    @Column(name = "outstanding", nullable = false, precision = 14, scale = 2)
    private BigDecimal outstanding = BigDecimal.ZERO;

    @Column(name = "profit", nullable = false, precision = 14, scale = 2)
    private BigDecimal profit = BigDecimal.ZERO;

    @Column(name = "customer_notes")
    private String customerNotes;

    @Column(name = "internal_notes")
    private String internalNotes;

    @Column(name = "expected_at")
    private Instant expectedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "idempotency_key", length = 80)
    private String idempotencyKey;
}
