package com.fixflow.user.service;

import com.fixflow.common.error.ApiException;
import com.fixflow.common.error.ErrorCode;
import com.fixflow.config.FixFlowProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Fills in the local {@code users} row for someone Identity knows about and this database does not.
 *
 * <p>MobiStack tables foreign-key to {@code users.id}. Those cannot point across a service boundary,
 * so each product keeps a thin mirror keyed by the Identity {@code sub}. The bulk import seeds it;
 * this covers everyone who signs up afterwards, on the first request that names them.
 *
 * <p>Written with SQL rather than through the repository on purpose. The row has to keep Identity's
 * id, and Hibernate's {@code @UuidGenerator} replaces an assigned identifier with a fresh one, so a
 * {@code save()} would silently produce a mirror under the wrong primary key.
 */
@Slf4j
@Service
public class IdentityUserMirror {

    /** Never used for sign-in; satisfies {@code password_hash NOT NULL} for Identity-only accounts. */
    private static final String UNUSABLE_PASSWORD_SEED = "identity-only-" + UUID.randomUUID();

    private final FixFlowProperties.Identity config;
    private final RestClient http;
    private final JdbcTemplate jdbc;
    private final String unusablePasswordHash;

    public IdentityUserMirror(FixFlowProperties properties,
                              JdbcTemplate jdbc,
                              PasswordEncoder passwordEncoder) {
        this.config = properties.getSecurity().getIdentity();
        this.jdbc = jdbc;
        this.http = RestClient.create();
        this.unusablePasswordHash = passwordEncoder.encode(UNUSABLE_PASSWORD_SEED);
    }

    /**
     * Creates or refreshes the mirror row for one Identity subject.
     *
     * @throws ApiException if Identity does not know the subject either, or cannot be asked
     */
    public void pull(UUID subject) {
        if (!config.canMirror()) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED,
                    "This account is not provisioned on MobiStack");
        }

        LookupResponse response;
        try {
            response = http.post()
                    .uri(trimSlash(config.getInternalBaseUrl()) + "/internal/users/lookup")
                    .header("X-Prabhix-Service-Token", config.getServiceToken())
                    .body(new LookupRequest(List.of(subject), List.of()))
                    .retrieve()
                    .body(LookupResponse.class);
        } catch (RuntimeException ex) {
            log.error("Could not reach identity to mirror user {}: {}", subject, ex.getMessage());
            throw new ApiException(ErrorCode.INTERNAL_ERROR,
                    "Could not verify your account right now. Try again.");
        }

        MirroredUser user = response == null || response.users() == null || response.users().isEmpty()
                ? null
                : response.users().get(0);
        if (user == null) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "That account no longer exists");
        }

        upsert(user);
        log.info("Mirrored identity user {} into MobiStack", subject);
    }

    private void upsert(MirroredUser user) {
        Instant now = Instant.now();
        boolean active = user.status() == null || "ACTIVE".equalsIgnoreCase(user.status());
        // system_admin is deliberately absent from both insert and update. Product authority stays
        // in this database: mirroring Identity's platform_admin would widen blast radius.
        jdbc.update("""
                INSERT INTO users (id, full_name, email, password_hash, avatar_url, active,
                                   must_change_pw, failed_logins, version, created_at, updated_at,
                                   email_verified, phone_verified, system_admin)
                VALUES (?, ?, ?, ?, ?, ?, false, 0, 0, ?, ?, ?, false, false)
                ON CONFLICT (id) DO UPDATE SET
                    email = EXCLUDED.email,
                    full_name = EXCLUDED.full_name,
                    avatar_url = EXCLUDED.avatar_url,
                    email_verified = EXCLUDED.email_verified,
                    active = EXCLUDED.active,
                    updated_at = EXCLUDED.updated_at
                """,
                user.id(),
                blankToPlaceholder(user.fullName(), user.email()),
                user.email(),
                unusablePasswordHash,
                truncate(user.avatarUrl(), 500),
                active,
                Timestamp.from(now),
                Timestamp.from(now),
                user.emailVerified());
    }

    private static String blankToPlaceholder(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    private static String trimSlash(String base) {
        if (base == null || base.isBlank()) {
            return "";
        }
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private record LookupRequest(List<UUID> ids, List<String> emails) {
    }

    private record LookupResponse(List<MirroredUser> users) {
    }

    private record MirroredUser(UUID id,
                                String email,
                                boolean emailVerified,
                                String fullName,
                                String displayName,
                                String avatarUrl,
                                String jobTitle,
                                String timezone,
                                String locale,
                                String status,
                                boolean platformAdmin,
                                Instant updatedAt) {
    }
}
