package com.fixflow.inventory.domain;

import com.fixflow.catalog.domain.StockStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "stock_alerts")
public class StockAlert {

    public enum AlertType {
        LOW_STOCK, OUT_OF_STOCK, DEAD_STOCK, OVERSTOCK
    }

    public enum Status {
        OPEN, ACKNOWLEDGED, RESOLVED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "shop_id", nullable = false)
    private UUID shopId;

    @Column(name = "product_variant_id", nullable = false)
    private UUID productVariantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, length = 24)
    private AlertType alertType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 12)
    private StockStatus severity = StockStatus.ORANGE;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status = Status.OPEN;

    @Column(name = "threshold_value")
    private Integer thresholdValue;

    @Column(name = "observed_value")
    private Integer observedValue;

    @Column(name = "message", nullable = false, length = 400)
    private String message;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "acknowledged_by")
    private UUID acknowledgedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
