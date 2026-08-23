package com.fixflow.notify.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "notification_preferences")
public class NotificationPreference {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(nullable = false)
    private boolean email = true;

    @Column(nullable = false)
    private boolean whatsapp;

    @Column(name = "push", nullable = false)
    private boolean push = true;

    @Column(nullable = false)
    private boolean sms;
}
