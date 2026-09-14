package com.fixflow.billing.service;

import com.fixflow.common.error.ApiException;
import com.fixflow.common.error.ErrorCode;
import com.fixflow.presence.PresenceService;
import com.fixflow.presence.PresenceSnapshot;
import com.fixflow.shop.domain.Shop;
import com.fixflow.shop.repository.ShopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * The paid "extra screens" add-on: how many people a shop may have working at once.
 *
 * <p>A screen seat is a person, not a device. Every shop has one seat included and buys more at a
 * flat monthly price; the same person on the counter phone and the web console uses one seat.
 * Sign-in itself is Prabhix Identity's and is never refused here. Seats are reported so the shop can
 * see how many are in use (from presence heartbeats) against how many it pays for, and the number
 * bought is what billing charges for.
 */
@Service
@RequiredArgsConstructor
public class ScreenSeatService {

    public static final int INCLUDED_SCREENS = 1;
    public static final int MAX_EXTRA_SCREENS = 49;
    public static final String EXTRA_SCREEN_PRICE = "EXTRA_SCREEN";
    public static final String EXTRA_SCREEN_RENEW = "EXTRA_SCREEN_RENEW";

    /**
     * @param included seats every shop has without paying
     * @param extra paid seats currently live (subscribed and inside the paid period)
     * @param subscribed paid seats bought, whether or not the period has lapsed
     * @param seats included plus live extra
     * @param inUse distinct people with a live presence heartbeat in this shop
     * @param live whether any extra seat is currently paid for
     * @param periodEnd when the paid period ends, or null when there is none
     */
    public record ScreenCapacity(int included, int extra, int subscribed, int seats, int inUse,
                                 boolean live, Instant periodEnd) {
        public boolean full() {
            return inUse >= seats;
        }
    }

    private final ShopRepository shopRepository;
    private final PresenceService presenceService;

    @Transactional(readOnly = true)
    public ScreenCapacity capacity(UUID shopId) {
        if (shopId == null) {
            return new ScreenCapacity(INCLUDED_SCREENS, 0, 0, INCLUDED_SCREENS, 0, false, null);
        }
        Shop shop = shopRepository.findById(shopId).orElse(null);
        int subscribed = shop == null ? 0 : Math.max(0, shop.getExtraScreens());
        int extra = shop == null ? 0 : shop.liveExtraScreens();
        int seats = INCLUDED_SCREENS + extra;
        int inUse = (int) presenceService.liveForWorkspace(shopId).stream()
                .map(PresenceSnapshot::userId)
                .distinct()
                .count();
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

    /** Platform staff override, used when a payment was taken outside Razorpay or to comp a shop. */
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
}
