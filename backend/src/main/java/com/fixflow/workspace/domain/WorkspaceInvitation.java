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

@Getter
@Setter
@Entity
@Table(name = "workspace_invitations")
public class WorkspaceInvitation extends AuditableEntity {

    public enum Status {
        PENDING, ACCEPTED, CANCELLED, EXPIRED
    }

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "phone", length = 32)
    private String phone;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Column(name = "token_hash", nullable = false, length = 88)
    private String tokenHash;

    @Column(name = "raw_hint", nullable = false, length = 12)
    private String rawHint;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "invited_by")
    private UUID invitedBy;

    @Column(name = "accepted_by")
    private UUID acceptedBy;
}
