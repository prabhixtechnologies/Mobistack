package com.fixflow.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "compatibility_change_requests")
public class CompatibilityChangeRequest {

    public enum Action {
        ADD_DEVICE, REMOVE_DEVICE
    }

    public enum Status {
        PENDING, APPROVED, REJECTED
    }

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "shop_id", nullable = false)
    private UUID shopId;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Action action;

    @Column(name = "device_id")
    private UUID deviceId;

    @Column(length = 255)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "requested_by")
    private UUID requestedBy;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "reviewed_at")
    private Instant reviewedAt;
}
