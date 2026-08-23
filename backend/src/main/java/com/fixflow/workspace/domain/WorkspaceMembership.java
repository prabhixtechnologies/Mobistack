package com.fixflow.workspace.domain;

import com.fixflow.common.domain.AuditableEntity;
import com.fixflow.user.domain.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * The only link between a platform user and a workspace. Role and status live
 * here so the same person can be OWNER in one shop and TECHNICIAN in another.
 */
@Getter
@Setter
@Entity
@Table(name = "workspace_memberships")
public class WorkspaceMembership extends AuditableEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MembershipStatus status = MembershipStatus.PENDING;

    @Column(name = "invited_by")
    private UUID invitedBy;

    @Column(name = "joined_at")
    private Instant joinedAt;

    @Column(name = "last_selected_at")
    private Instant lastSelectedAt;

    public boolean isActive() {
        return status == MembershipStatus.ACTIVE;
    }
}
