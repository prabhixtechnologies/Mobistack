package com.fixflow.security.jwt;

import com.fixflow.common.error.ApiException;
import com.fixflow.common.error.ErrorCode;
import com.fixflow.config.FixFlowProperties;
import com.fixflow.security.Permission;
import com.fixflow.security.UserPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Issues short-lived stateless access tokens (JWT) and opaque refresh tokens.
 *
 * <p>Refresh tokens are deliberately <em>not</em> JWTs: they must be revocable,
 * and a random 256-bit string stored as a hash gives us that for free.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JwtService {

    private static final int MIN_SECRET_LENGTH = 64;
    private static final String CLAIM_SHOP = "shop";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_NAME = "name";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_PERMISSIONS = "perms";

    private final FixFlowProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    private SecretKey signingKey;

    @PostConstruct
    void init() {
        String secret = properties.getSecurity().getJwt().getSecret();
        if (secret == null || secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "fixflow.security.jwt.secret must be at least " + MIN_SECRET_LENGTH + " characters");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(UserPrincipal principal) {
        Instant now = Instant.now();
        Duration ttl = properties.getSecurity().getJwt().getAccessTokenTtl();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(principal.getId().toString())
                .issuer(properties.getSecurity().getJwt().getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .claim(CLAIM_SHOP, principal.getShopId() == null ? "" : principal.getShopId().toString())
                .claim(CLAIM_EMAIL, principal.getEmail())
                .claim(CLAIM_NAME, principal.getFullName())
                .claim(CLAIM_ROLES, List.copyOf(principal.getRoles()))
                .claim(CLAIM_PERMISSIONS, principal.getPermissions().stream().map(Enum::name).toList())
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Rebuilds the principal straight from the token. No database round-trip on
     * the hot path; the short access-token TTL bounds how long a revoked user
     * stays usable.
     */
    public UserPrincipal parseAccessToken(String token) {
        Claims claims = parseClaims(token);
        Set<String> roles = new LinkedHashSet<>();
        List<?> rawRoles = claims.get(CLAIM_ROLES, List.class);
        if (rawRoles != null) {
            rawRoles.forEach(role -> roles.add(String.valueOf(role)));
        }
        Set<Permission> permissions = new LinkedHashSet<>();
        List<?> rawPerms = claims.get(CLAIM_PERMISSIONS, List.class);
        if (rawPerms != null) {
            for (Object raw : rawPerms) {
                try {
                    permissions.add(Permission.valueOf(String.valueOf(raw)));
                } catch (IllegalArgumentException ex) {
                    log.debug("Unknown permission {} in token", raw);
                }
            }
        }
        String shopClaim = claims.get(CLAIM_SHOP, String.class);
        UUID workspaceId = (shopClaim == null || shopClaim.isBlank()) ? null : UUID.fromString(shopClaim);
        return new UserPrincipal(
                UUID.fromString(claims.getSubject()),
                workspaceId,
                claims.get(CLAIM_EMAIL, String.class),
                claims.get(CLAIM_NAME, String.class),
                true,
                roles,
                permissions);
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(properties.getSecurity().getJwt().getIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException ex) {
            throw new ApiException(ErrorCode.TOKEN_EXPIRED, "Access token has expired");
        } catch (JwtException | IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.TOKEN_INVALID, "Access token is not valid");
        }
    }

    public long accessTokenTtlSeconds() {
        return properties.getSecurity().getJwt().getAccessTokenTtl().toSeconds();
    }

    public Instant refreshTokenExpiry() {
        return Instant.now().plus(properties.getSecurity().getJwt().getRefreshTokenTtl());
    }

    /** 256 bits of entropy, URL-safe so it survives being put in a header. */
    public String generateRefreshToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hashRefreshToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required but unavailable", ex);
        }
    }
}
