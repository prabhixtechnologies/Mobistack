package com.fixflow.auth.domain;

import com.fixflow.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Only the SHA-256 hash of the token is stored, so a database leak does not
 * hand out live sessions. Rotation is tracked via {@code replacedBy} to detect
 * replay of an already-used token.
 */
@Getter
@Setter
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, length = 88)
    private String tokenHash;

    @Column(name = "device_id", length = 120)
    private String deviceId;

    @Column(name = "user_agent", length = 400)
    private String userAgent;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by")
    private UUID replacedBy;

    public boolean isActive() {
        return revokedAt == null && expiresAt.isAfter(Instant.now());
    }
}
