package com.fixflow.support.domain;

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
@Table(name = "support_conversations")
public class SupportConversation extends BaseEntity {

    public static final String OPEN = "OPEN";
    public static final String WAITING = "WAITING";
    public static final String RESOLVED = "RESOLVED";

    @Column(name = "shop_id")
    private UUID shopId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "subject", nullable = false, length = 200)
    private String subject;

    @Column(name = "status", nullable = false, length = 20)
    private String status = OPEN;

    @Column(name = "channel", nullable = false, length = 20)
    private String channel = "WEB";

    @Column(name = "assigned_to")
    private UUID assignedTo;

    @Column(name = "last_message_at", nullable = false)
    private Instant lastMessageAt = Instant.now();
}
