package com.fixflow.group.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A shop asking to share one fitment group's compatibility. Payment is captured before this row
 * is pending. An owner or admin of the group admits it; until then the shop cannot read the group.
 */
@Getter
@Setter
@Entity
@Table(name = "group_join_requests")
public class GroupJoinRequest {

    public static final String PENDING = "PENDING";
    public static final String REJECTED = "REJECTED";
    public static final String CANCELLED = "CANCELLED";
    public static final String ADMITTED = "ADMITTED";

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "shop_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
