package com.fixflow.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "user_tokens")
public class UserToken {

    public static final String PASSWORD_RESET = "PASSWORD_RESET";
    public static final String EMAIL_VERIFY = "EMAIL_VERIFY";
    public static final String PHONE_OTP = "PHONE_OTP";
    public static final String EMAIL_OTP = "EMAIL_OTP";
    public static final String WHATSAPP_OTP = "WHATSAPP_OTP";
    public static final String MAGIC_LINK = "MAGIC_LINK";

    @Id
    @Column(name = "id", nullable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "phone", length = 32)
    private String phone;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "token_type", nullable = false, length = 20)
    private String tokenType;

    @Column(name = "token_hash", nullable = false, length = 88)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    /**
     * Wrong guesses made against this code. A six-digit code with no counter can
     * be brute-forced inside its own lifetime by a caller who spreads attempts
     * across enough addresses to stay under the per-IP limit.
     */
    @Column(name = "attempts", nullable = false)
    private short attempts;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
