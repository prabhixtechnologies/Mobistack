package com.fixflow.billing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "billing_webhook_events")
public class BillingWebhookEvent {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, length = 40)
    private String provider;

    @Column(name = "event_id", nullable = false, length = 80)
    private String eventId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> payload = Map.of();

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
