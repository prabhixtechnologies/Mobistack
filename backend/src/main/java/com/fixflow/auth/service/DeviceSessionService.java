package com.fixflow.auth.service;

import com.fixflow.auth.domain.RefreshToken;
import com.fixflow.auth.repository.RefreshTokenRepository;
import com.fixflow.common.error.ApiException;
import com.fixflow.common.error.ErrorCode;
import com.fixflow.shop.domain.Shop;
import com.fixflow.shop.repository.ShopRepository;
import com.fixflow.workspace.domain.MembershipStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class DeviceSessionService {

    public static final int INCLUDED_SCREENS = 1;
    public static final int MAX_EXTRA_SCREENS = 49;
    public static final String EXTRA_SCREEN_PRICE = "EXTRA_SCREEN";
    public static final String EXTRA_SCREEN_RENEW = "EXTRA_SCREEN_RENEW";

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

    public record ScreenCapacity(int included, int extra, int subscribed, int seats, int inUse,
                                 boolean live, Instant periodEnd) {
        public boolean full() {
            return inUse >= seats;
        }
    }

    private final RefreshTokenRepository refreshTokenRepository;
    private final ShopRepository shopRepository;
    private final Map<UUID, String> liveDeviceByUser = new ConcurrentHashMap<>();

    public String resolveDeviceId(AuthService.ClientInfo client) {
        if (client != null && client.deviceId() != null && !client.deviceId().isBlank()) {
            return truncate(client.deviceId().trim(), 80);
        }
        String seed = (client == null ? "" : nullToEmpty(client.userAgent()) + "|" + nullToEmpty(client.ipAddress()));
        return "anon-" + sha256(seed).substring(0, 16);
    }

    /**
     * One live session per user. A second sign-in on another screen ends the
     * first. A shop may only have as many different people signed in as it has
     * paid screen seats (one included, plus ₹50 extras).
     */
    @Transactional
    public String register(UUID userId, UUID shopId, AuthService.ClientInfo client, boolean bypassSeatLimit) {
        String deviceId = resolveDeviceId(client);
        Instant now = Instant.now();
        boolean alreadySeated = refreshTokenRepository
                .existsByUserIdAndRevokedAtIsNullAndExpiresAtAfter(userId, now);

        if (shopId != null && !bypassSeatLimit && !alreadySeated) {
            ScreenCapacity capacity = capacity(shopId);
            long others = refreshTokenRepository.countOtherSeatedUsers(
                    shopId, userId, MembershipStatus.ACTIVE, now);
            if (others >= capacity.seats()) {
                throw new ApiException(ErrorCode.DEVICE_LIMIT_REACHED,
                        "This shop has " + capacity.seats() + " screen"
                                + (capacity.seats() == 1 ? "" : "s")
                                + " and they are all signed in. Buy another screen for ₹50/month on Billing.");
            }
        }

        refreshTokenRepository.revokeAllForUser(userId, now);
        liveDeviceByUser.put(userId, deviceId);
        return deviceId;
    }

    @Transactional(readOnly = true)
    public boolean isLive(UUID userId, String deviceId) {
        if (userId == null || deviceId == null || deviceId.isBlank()) {
            return true;
        }
        String known = liveDeviceByUser.get(userId);
        if (known != null) {
            return known.equals(deviceId);
        }
        boolean live = refreshTokenRepository.existsByUserIdAndDeviceIdAndRevokedAtIsNullAndExpiresAtAfter(
                userId, deviceId, Instant.now());
        if (live) {
            liveDeviceByUser.put(userId, deviceId);
        }
        return live;
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
        forgetIfMatch(userId, token.getDeviceId());
    }

    @Transactional
    public int revokeUserDevice(UUID userId, String deviceId) {
        int revoked = refreshTokenRepository.revokeByUserAndDevice(userId, deviceId, Instant.now());
        forgetIfMatch(userId, deviceId);
        return revoked;
    }

    @Transactional
    public int revokeAll(UUID userId) {
        liveDeviceByUser.remove(userId);
        return refreshTokenRepository.revokeAllForUser(userId, Instant.now());
    }

    @Transactional(readOnly = true)
    public ScreenCapacity capacity(UUID shopId) {
        if (shopId == null) {
            return new ScreenCapacity(INCLUDED_SCREENS, 0, 0, INCLUDED_SCREENS, 0, false, null);
        }
        Shop shop = shopRepository.findById(shopId).orElse(null);
        int subscribed = shop == null ? 0 : Math.max(0, shop.getExtraScreens());
        int extra = shop == null ? 0 : shop.liveExtraScreens();
        int seats = INCLUDED_SCREENS + extra;
        int inUse = (int) refreshTokenRepository.countSeatedUsers(shopId, MembershipStatus.ACTIVE, Instant.now());
        Instant periodEnd = shop == null ? null : shop.getExtraScreensPeriodEnd();
        return new ScreenCapacity(INCLUDED_SCREENS, extra, subscribed, seats, inUse, extra > 0, periodEnd);
    }

    @Transactional
    public int addExtraScreen(UUID shopId) {
        Shop shop = requireShop(shopId);
        if (!shop.extraScreensLive() && shop.getExtraScreens() > 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "This month's extra screens have lapsed. Pay this month to turn them back on, then add another.");
        }
        if (shop.getExtraScreens() >= MAX_EXTRA_SCREENS) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "This shop already has the maximum number of screens.");
        }
        shop.setExtraScreens(shop.getExtraScreens() + 1);
        shop.setMaxDevicesPerUser(1);
        if (shop.getExtraScreensPeriodEnd() == null || !shop.getExtraScreensPeriodEnd().isAfter(Instant.now())) {
            shop.setExtraScreensPeriodEnd(Instant.now().plus(31, ChronoUnit.DAYS));
        }
        shopRepository.save(shop);
        return shop.getExtraScreens();
    }

    @Transactional
    public int renewExtraScreens(UUID shopId) {
        Shop shop = requireShop(shopId);
        if (shop.getExtraScreens() < 1) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "This shop has no extra screens to renew. Add one first.");
        }
        Instant now = Instant.now();
        Instant current = shop.getExtraScreensPeriodEnd();
        Instant base = current != null && current.isAfter(now) ? current : now;
        shop.setExtraScreensPeriodEnd(base.plus(31, ChronoUnit.DAYS));
        shop.setMaxDevicesPerUser(1);
        shopRepository.save(shop);
        return shop.getExtraScreens();
    }

    @Transactional
    public int setExtraScreens(UUID shopId, int extraScreens) {
        Shop shop = requireShop(shopId);
        int extra = Math.max(0, Math.min(MAX_EXTRA_SCREENS, extraScreens));
        shop.setExtraScreens(extra);
        shop.setMaxDevicesPerUser(1);
        if (extra == 0) {
            shop.setExtraScreensPeriodEnd(null);
        }
        shopRepository.save(shop);
        return extra;
    }

    private Shop requireShop(UUID shopId) {
        return shopRepository.findById(shopId)
                .orElseThrow(() -> ApiException.notFound("Shop", shopId));
    }

    private void forgetIfMatch(UUID userId, String deviceId) {
        liveDeviceByUser.compute(userId, (id, current) ->
                current != null && current.equals(deviceId) ? null : current);
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
