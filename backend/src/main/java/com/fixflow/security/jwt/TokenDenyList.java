package com.fixflow.security.jwt;

import com.prabhix.identity.client.IdentityToken;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Reads the revocation list Identity writes.
 *
 * <p>The keys are {@code pbx:deny:session:} and {@code pbx:deny:user:}. A missing Redis connection
 * fails open: a cache outage must not sign every shop out, and the access token still expires in
 * fifteen minutes.
 */
@Slf4j
@Component
public class TokenDenyList {

    private static final String SESSION_KEY = "pbx:deny:session:";
    private static final String USER_KEY = "pbx:deny:user:";
    private static final long MIN_PLAUSIBLE_EPOCH_MILLIS = 1_000_000_000_000L;

    private final ObjectProvider<StringRedisTemplate> redis;

    public TokenDenyList(ObjectProvider<StringRedisTemplate> redis) {
        this.redis = redis;
    }

    public void revokeSession(UUID sessionId) {
        if (sessionId == null) {
            return;
        }
        StringRedisTemplate cache = redis.getIfAvailable();
        if (cache == null) {
            return;
        }
        try {
            cache.opsForValue().set(SESSION_KEY + sessionId, "1", Duration.ofMinutes(16));
        } catch (RuntimeException ex) {
            log.warn("Could not write the session deny-list entry: {}", ex.getMessage());
        }
    }

    public boolean isRevoked(IdentityToken token) {
        if (token == null) {
            return false;
        }
        StringRedisTemplate cache = redis.getIfAvailable();
        if (cache == null) {
            return false;
        }
        try {
            if (token.sessionId() != null
                    && Boolean.TRUE.equals(cache.hasKey(SESSION_KEY + token.sessionId()))) {
                return true;
            }
            if (token.subject() == null) {
                return false;
            }
            String revokedAt = cache.opsForValue().get(USER_KEY + token.subject());
            if (revokedAt == null) {
                return false;
            }
            return issuedBefore(token.issuedAt(), revokedAt);
        } catch (RuntimeException ex) {
            log.warn("Deny-list check failed, allowing the request through: {}", ex.getMessage());
            return false;
        }
    }

    private static boolean issuedBefore(Instant issuedAt, String revokedAt) {
        if (issuedAt == null) {
            return true;
        }
        long revokedAtMillis;
        try {
            revokedAtMillis = Long.parseLong(revokedAt);
        } catch (NumberFormatException ex) {
            return true;
        }
        if (revokedAtMillis < MIN_PLAUSIBLE_EPOCH_MILLIS) {
            return true;
        }
        return issuedAt.toEpochMilli() < revokedAtMillis;
    }

    /** Visible for tests that want to name the key Identity writes. */
    public static String sessionKey(UUID sessionId) {
        return SESSION_KEY + sessionId;
    }
}
