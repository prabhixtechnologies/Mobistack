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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

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
    private final com.fixflow.config.FixFlowProperties properties;
    private final com.fixflow.notify.WorkspaceNotifier notifier;

    public String resolveDeviceId(AuthService.ClientInfo client) {
        if (client != null && client.deviceId() != null && !client.deviceId().isBlank()) {
            return truncate(client.deviceId().trim(), 80);
        }
        String seed = (client == null ? "" : nullToEmpty(client.userAgent()) + "|" + nullToEmpty(client.ipAddress()));
        return "anon-" + sha256(seed).substring(0, 16);
    }

    /**
     * Seats a sign-in on one device.
     *
     * <p>Two different limits apply, and keeping them separate matters. A
     * <em>screen seat</em> is a person: a shop may have as many different people
     * signed in at once as it has paid seats (one included, plus ₹50 extras). A
     * <em>device</em> is a screen belonging to one of those people: the same
     * person may use the web console and their own phone together without
     * consuming a second seat, up to the shop's per-user device cap.
     *
     * <p>Only the tokens for the device being seated are revoked, so signing in
     * or refreshing on one device never ends a session on another.
     */
    @Transactional
    public String register(UUID userId, UUID shopId, AuthService.ClientInfo client, boolean bypassSeatLimit) {
        String deviceId = resolveDeviceId(client);
        Instant now = Instant.now();
        List<RefreshToken> live = refreshTokenRepository
                .findByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtAsc(userId, now);
        boolean knownDevice = live.stream().anyMatch(token -> deviceId.equals(token.getDeviceId()));

        if (shopId != null && !bypassSeatLimit && live.isEmpty()) {
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

        if (!knownDevice) {
            enforceDeviceCap(userId, shopId, deviceId, live, now, bypassSeatLimit);
        }

        // Replace only this device's chain. Other devices keep their sessions.
        refreshTokenRepository.revokeByUserAndDevice(userId, deviceId, now);
        return deviceId;
    }

    /**
     * Keeps a user within their device cap by ending the least recently started
     * device, which is what the product promises. When {@code over-limit} is
     * configured as {@code reject} the new sign-in is refused instead.
     */
    private void enforceDeviceCap(UUID userId, UUID shopId, String deviceId, List<RefreshToken> live,
                                  Instant now, boolean bypassCap) {
        if (bypassCap) {
            return;
        }
        int cap = deviceCap(shopId);
        LinkedHashSet<String> devices = new LinkedHashSet<>();
        for (RefreshToken token : live) {
            if (token.getDeviceId() != null && !deviceId.equals(token.getDeviceId())) {
                devices.add(token.getDeviceId());
            }
        }
        if (devices.size() < cap) {
            return;
        }
        if (!"evict".equalsIgnoreCase(properties.getDevices().getOverLimit())) {
            throw new ApiException(ErrorCode.DEVICE_LIMIT_REACHED,
                    "You are signed in on " + devices.size() + " device"
                            + (devices.size() == 1 ? "" : "s")
                            + " already. Sign out of one from Profile, then try again.");
        }
        // Oldest first: drop as many as needed to make room for this device.
        int surplus = devices.size() - cap + 1;
        for (String stale : devices) {
            if (surplus-- <= 0) {
                break;
            }
            refreshTokenRepository.revokeByUserAndDevice(userId, stale, now);
        }
    }

    private int deviceCap(UUID shopId) {
        int absolute = Math.max(1, properties.getDevices().getAbsoluteMaxPerUser());
        int configured = properties.getDevices().getDefaultMaxPerUser();
        if (shopId != null) {
            Shop shop = shopRepository.findById(shopId).orElse(null);
            if (shop != null && shop.getMaxDevicesPerUser() > 0) {
                configured = shop.getMaxDevicesPerUser();
            }
        }
        return Math.min(absolute, Math.max(1, configured));
    }

    /**
     * True when the access token's device still holds a live refresh token.
     * Read straight from the database so the answer is the same on every
     * instance and survives a restart.
     */
    @Transactional(readOnly = true)
    public boolean isLive(UUID userId, String deviceId) {
        if (userId == null || deviceId == null || deviceId.isBlank()) {
            return true;
        }
        return refreshTokenRepository.existsByUserIdAndDeviceIdAndRevokedAtIsNullAndExpiresAtAfter(
                userId, deviceId, Instant.now());
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
        // Signing a device out is also what you do after losing a phone, so the
        // account holder is told which device went and when.
        notifier.toUser(null, userId, "DEVICE_REVOKED",
                "A device was signed out",
                "%s was signed out of MobiStack. If that was not you, change your password now."
                        .formatted(describe(token)),
                "/profile");
    }

    private static String describe(RefreshToken token) {
        if (token.getUserAgent() != null && !token.getUserAgent().isBlank()) {
            return token.getUserAgent().length() > 60
                    ? token.getUserAgent().substring(0, 60) : token.getUserAgent();
        }
        return token.getDeviceId() == null ? "A device" : "Device " + token.getDeviceId();
    }

    @Transactional
    public int revokeUserDevice(UUID userId, String deviceId) {
        return refreshTokenRepository.revokeByUserAndDevice(userId, deviceId, Instant.now());
    }

    @Transactional
    public int revokeAll(UUID userId) {
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
        shopRepository.save(shop);
        return shop.getExtraScreens();
    }

    @Transactional
    public int setExtraScreens(UUID shopId, int extraScreens) {
        Shop shop = requireShop(shopId);
        int extra = Math.max(0, Math.min(MAX_EXTRA_SCREENS, extraScreens));
        shop.setExtraScreens(extra);
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
