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
@Table(name = "inbox_notifications")
public class InboxNotification extends BaseEntity {

    @Column(name = "shop_id")
    private UUID shopId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "body")
    private String body;

    @Column(name = "link", length = 400)
    private String link;

    @Column(name = "read_at")
    private Instant readAt;
}
