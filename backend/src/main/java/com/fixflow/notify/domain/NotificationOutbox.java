package com.fixflow.notify.domain;

import com.fixflow.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "notification_outbox")
public class NotificationOutbox extends BaseEntity {

    @Column(name = "shop_id")
    private UUID shopId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(name = "channel", nullable = false, length = 20)
    private String channel;

    @Column(name = "recipient", length = 255)
    private String recipient;

    @Column(name = "subject", length = 255)
    private String subject;

    @Column(name = "body", nullable = false)
    private String body;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "provider", length = 40)
    private String provider;

    @Column(name = "provider_ref", length = 80)
    private String providerRef;

    @Column(name = "sent_at")
    private Instant sentAt;
}
