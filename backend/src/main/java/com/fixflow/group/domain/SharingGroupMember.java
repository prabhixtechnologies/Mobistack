package com.fixflow.group.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Either a whole shop or one person. A shop membership is always {@link GroupRole#MEMBER}:
 * everyone in that shop can read the fitment, and nobody in the shop becomes an admin by joining.
 */
@Getter
@Setter
@Entity
@Table(name = "sharing_group_members")
public class SharingGroupMember {

    public enum Kind {
        SHOP,
        PERSON
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "shop_id")
    private UUID workspaceId;

    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private GroupRole role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Kind kind() {
        return workspaceId != null ? Kind.SHOP : Kind.PERSON;
    }
}
