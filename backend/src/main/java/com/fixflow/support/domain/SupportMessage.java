package com.fixflow.support.domain;

import com.fixflow.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "support_messages")
public class SupportMessage extends BaseEntity {

    public static final String USER = "USER";
    public static final String AGENT = "AGENT";
    public static final String BOT = "BOT";

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "author_type", nullable = false, length = 12)
    private String authorType;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "body", nullable = false)
    private String body;
}
