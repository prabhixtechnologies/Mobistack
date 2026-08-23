package com.fixflow.auth.service;

import com.fixflow.auth.domain.RefreshToken;
import com.fixflow.auth.repository.RefreshTokenRepository;
import com.fixflow.common.error.ApiException;
import com.fixflow.common.error.ErrorCode;
import com.fixflow.config.FixFlowProperties;
import com.fixflow.shop.domain.Shop;
import com.fixflow.shop.repository.ShopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeviceSessionService {

    public record SessionCard(
            UUID id,
            String deviceId,
            String userAgent,
            String ipAddress,
            Instant createdAt,
            Instant expiresAt,
            boolean current
    ) {
    }

    private final RefreshTokenRepository refreshTokenRepository;
    private final ShopRepository shopRepository;
    private final FixFlowProperties properties;

    public String resolveDeviceId(AuthService.ClientInfo client) {
        if (client != null && client.deviceId() != null && !client.deviceId().isBlank()) {
            return truncate(client.deviceId().trim(), 80);
        }
        String seed = (client == null ? "" : nullToEmpty(client.userAgent()) + "|" + nullToEmpty(client.ipAddress()));
        return "anon-" + sha256(seed).substring(0, 16);
    }

    /**
     * One live refresh token per device. If the account is at the shop cap,
     * either drop the oldest device or refuse the new sign-in.
     */
    @Transactional
    public String register(UUID userId, UUID shopId, AuthService.ClientInfo client) {
        String deviceId = resolveDeviceId(client);
        Instant now = Instant.now();
        refreshTokenRepository.revokeByUserAndDevice(userId, deviceId, now);

        int max = resolveMax(shopId);
        List<RefreshToken> active = refreshTokenRepository
                .findByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtAsc(userId, now);
        Set<String> otherDevices = new LinkedHashSet<>();
        for (RefreshToken token : active) {
            String id = token.getDeviceId() == null || token.getDeviceId().isBlank() ? token.getId().toString()
                    : token.getDeviceId();
            if (!deviceId.equals(id)) {
                otherDevices.add(id);
            }
        }
        if (otherDevices.size() >= max) {
            if ("reject".equalsIgnoreCase(properties.getDevices().getOverLimit())) {
                throw new ApiException(ErrorCode.DEVICE_LIMIT_REACHED,
                        "This account is already signed in on " + max + " devices. Sign out one of them first.");
            }
            String oldest = otherDevices.iterator().next();
            refreshTokenRepository.revokeByUserAndDevice(userId, oldest, now);
        }
        return deviceId;
    }

    @Transactional(readOnly = true)
    public List<SessionCard> listMine(UUID userId, String currentDeviceId) {
        Instant now = Instant.now();
        return refreshTokenRepository
                .findByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtAsc(userId, now)
                .stream()
                .map(token -> new SessionCard(token.getId(), token.getDeviceId(), token.getUserAgent(),
                        token.getIpAddress(), token.getCreatedAt(), token.getExpiresAt(),
                        currentDeviceId != null && currentDeviceId.equals(token.getDeviceId())))
                .toList();
    }

    @Transactional
    public void revoke(UUID userId, UUID sessionId) {
        RefreshToken token = refreshTokenRepository.findById(sessionId)
                .orElseThrow(() -> ApiException.notFound("Session", sessionId));
        if (!token.getUserId().equals(userId)) {
            throw ApiException.forbidden("That session does not belong to you.");
        }
        token.setRevokedAt(Instant.now());
        refreshTokenRepository.save(token);
    }

    @Transactional
    public int revokeUserDevice(UUID userId, String deviceId) {
        return refreshTokenRepository.revokeByUserAndDevice(userId, deviceId, Instant.now());
    }

    @Transactional
    public int revokeAll(UUID userId) {
        return refreshTokenRepository.revokeAllForUser(userId, Instant.now());
    }

    public int resolveMax(UUID shopId) {
        int fallback = properties.getDevices().getDefaultMaxPerUser();
        int absolute = properties.getDevices().getAbsoluteMaxPerUser();
        int resolved = fallback;
        if (shopId != null) {
            resolved = shopRepository.findById(shopId).map(Shop::getMaxDevicesPerUser).orElse(fallback);
        }
        if (resolved < 1) {
            resolved = 1;
        }
        return Math.min(resolved, absolute);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
