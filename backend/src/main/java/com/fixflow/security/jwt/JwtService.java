package com.fixflow.security.jwt;

import com.fixflow.common.error.ApiException;
import com.fixflow.common.error.ErrorCode;
import com.fixflow.config.FixFlowProperties;
import com.fixflow.security.Permission;
import com.fixflow.security.UserPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.LocatorAdapter;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Issues short-lived HS256 access tokens and verifies both those and Identity's RS256 tokens.
 *
 * <p>Refresh tokens are deliberately <em>not</em> JWTs: they must be revocable,
 * and a random 256-bit string stored as a hash gives us that for free.
 */
@Slf4j
@Service
public class JwtService {

    private static final int MIN_SECRET_LENGTH = 64;
    private static final String CLAIM_SHOP = "shop";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_NAME = "name";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_PERMISSIONS = "perms";
    private static final String CLAIM_DEVICE = "dev";

    private final FixFlowProperties properties;
    private final IdentityKeySource identityKeys;
    private final SecureRandom secureRandom = new SecureRandom();

    private SecretKey signingKey;

    public JwtService(FixFlowProperties properties, IdentityKeySource identityKeys) {
        this.properties = properties;
        this.identityKeys = identityKeys;
    }

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
        return createAccessToken(principal, null);
    }

    public String createAccessToken(UserPrincipal principal, String deviceId) {
        Instant now = Instant.now();
        Duration ttl = properties.getSecurity().getJwt().getAccessTokenTtl();
        var builder = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(principal.getId().toString())
                .issuer(properties.getSecurity().getJwt().getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .claim(CLAIM_SHOP, principal.getShopId() == null ? "" : principal.getShopId().toString())
                .claim(CLAIM_EMAIL, principal.getEmail())
                .claim(CLAIM_NAME, principal.getFullName())
                .claim(CLAIM_ROLES, List.copyOf(principal.getRoles()))
                .claim(CLAIM_PERMISSIONS, principal.getPermissions().stream().map(Enum::name).toList());
        if (deviceId != null && !deviceId.isBlank()) {
            builder.claim(CLAIM_DEVICE, deviceId);
        }
        return builder.signWith(signingKey, Jwts.SIG.HS256).compact();
    }

    public String deviceIdFrom(String token) {
        return parseVerified(token).claims().get(CLAIM_DEVICE, String.class);
    }

    /**
     * Rebuilds the principal from the token. For HS256 tokens that includes shop, roles and
     * permissions; for Identity RS256 tokens those are left empty for the filter to resolve.
     */
    public UserPrincipal parseAccessToken(String token) {
        return parseDetailed(token).principal();
    }

    /**
     * Like {@link #parseAccessToken(String)} but also reports which issuer signed the token.
     *
     * @throws ApiException with {@link ErrorCode#TOKEN_EXPIRED} or {@link ErrorCode#TOKEN_INVALID}
     */
    public ParsedToken parseDetailed(String token) {
        Verified verified = parseVerified(token);
        Claims claims = verified.claims();
        boolean fromIdentity = verified.source() == TokenSource.IDENTITY;

        if (fromIdentity) {
            // An identity token states who you are and nothing about what you may do. Shop and
            // permissions are left empty here and filled in per request by JwtAuthenticationFilter.
            UserPrincipal principal = new UserPrincipal(
                    uuid(claims.getSubject()),
                    null,
                    claims.get(CLAIM_EMAIL, String.class),
                    claims.get(CLAIM_NAME, String.class),
                    true,
                    Set.of(),
                    Set.of());
            return new ParsedToken(principal, verified.source());
        }

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
        UserPrincipal principal = new UserPrincipal(
                UUID.fromString(claims.getSubject()),
                workspaceId,
                claims.get(CLAIM_EMAIL, String.class),
                claims.get(CLAIM_NAME, String.class),
                true,
                roles,
                permissions);
        return new ParsedToken(principal, TokenSource.MOBISTACK);
    }

    /**
     * Verifies the signature and the issuer, and reports which issuer it turned out to be.
     *
     * <p>Two signature families are accepted, and the token is never allowed to choose between them.
     * The key is selected by the {@code alg} in the header, and jjwt then enforces that the key
     * matches the algorithm family — an HMAC key can only satisfy a MAC algorithm and a public key
     * only a signature algorithm.
     *
     * <p>Issuer is checked after parsing rather than with {@code requireIssuer}, because there are now
     * two acceptable issuers and a token must match the one belonging to the key that verified it.
     */
    private Verified parseVerified(String token) {
        String mobistackIssuer = properties.getSecurity().getJwt().getIssuer();
        var identity = properties.getSecurity().getIdentity();

        try {
            Jws<Claims> jws = Jwts.parser()
                    .keyLocator(new LocatorAdapter<Key>() {
                        @Override
                        protected Key locate(JwsHeader header) {
                            if (Jwts.SIG.HS256.getId().equals(header.getAlgorithm())) {
                                return signingKey;
                            }
                            if (Jwts.SIG.RS256.getId().equals(header.getAlgorithm())) {
                                if (!identity.enabled()) {
                                    throw new SignatureException(
                                            "This deployment does not trust an identity issuer");
                                }
                                return identityKeys.verificationKey(header.getKeyId())
                                        .orElseThrow(() -> new SignatureException(
                                                "No published identity key with id "
                                                        + header.getKeyId()));
                            }
                            throw new SignatureException(
                                    "Unsupported token algorithm " + header.getAlgorithm());
                        }
                    })
                    .build()
                    .parseSignedClaims(token);

            boolean fromIdentity = Jwts.SIG.RS256.getId().equals(jws.getHeader().getAlgorithm());
            String expectedIssuer = fromIdentity ? identity.getIssuer() : mobistackIssuer;
            if (!expectedIssuer.equals(jws.getPayload().getIssuer())) {
                throw new ApiException(ErrorCode.TOKEN_INVALID, "That token is not valid");
            }

            return new Verified(jws.getPayload(),
                    fromIdentity ? TokenSource.IDENTITY : TokenSource.MOBISTACK);
        } catch (ExpiredJwtException ex) {
            throw new ApiException(ErrorCode.TOKEN_EXPIRED, "Access token has expired");
        } catch (ApiException ex) {
            throw ex;
        } catch (JwtException | IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.TOKEN_INVALID, "Access token is not valid");
        }
    }

    private UUID uuid(String value) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.TOKEN_INVALID, "Access token is not valid");
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
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

    private record Verified(Claims claims, TokenSource source) {
    }

    /** Which issuer signed a token, and therefore whether its claims may be trusted for authority. */
    public enum TokenSource {
        /** Signed HS256 by this service. Carries shop, roles and permissions. */
        MOBISTACK,
        /** Signed RS256 by Prabhix Identity. Carries identity only. */
        IDENTITY
    }

    /**
     * @param source which issuer signed it. An {@link TokenSource#IDENTITY} principal arrives with no
     *     shop and no permissions, and the caller is responsible for resolving both.
     */
    public record ParsedToken(UserPrincipal principal, TokenSource source) {
    }
}
