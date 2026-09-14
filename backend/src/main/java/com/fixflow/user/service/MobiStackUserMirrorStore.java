package com.fixflow.user.service;

import com.prabhix.identity.client.IdentityUser;
import com.prabhix.identity.client.UserMirrorStore;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * Writes the local {@code users} row for someone Prabhix Identity knows about.
 *
 * <p>MobiStack tables foreign-key to {@code users.id}. Those cannot point across a service boundary,
 * so this product keeps a thin mirror keyed by the Identity {@code sub}. The starter's
 * {@code IdentityUserMirror} decides when to ask Identity; this is only where the answer lands.
 *
 * <p>Written with SQL rather than through the repository on purpose. The row has to keep Identity's
 * id, and Hibernate's {@code @UuidGenerator} replaces an assigned identifier with a fresh one, so a
 * {@code save()} would silently produce a mirror under the wrong primary key.
 */
@Component
@RequiredArgsConstructor
public class MobiStackUserMirrorStore implements UserMirrorStore {

    private final JdbcTemplate jdbc;

    @Override
    public void upsert(IdentityUser user) {
        Instant now = Instant.now();
        // Identity's platform_admin flag is deliberately not copied anywhere. Platform staff
        // authority lives in the oneOps database and reaches MobiStack only over the service token.
        // A mirrored user starts with no shop: membership is granted by an invitation or by
        // creating a shop, never by existing in Identity.
        jdbc.update("""
                INSERT INTO users (id, full_name, email, avatar_url, active, version,
                                   created_at, updated_at, email_verified, phone_verified)
                VALUES (?, ?, ?, ?, ?, 0, ?, ?, ?, false)
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
                truncate(user.avatarUrl(), 500),
                user.isActive(),
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
}
