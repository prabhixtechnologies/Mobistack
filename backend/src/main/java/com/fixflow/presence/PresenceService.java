package com.fixflow.presence;

import com.fixflow.security.CurrentUser;
import com.fixflow.security.UserPrincipal;
import com.fixflow.shop.repository.ShopRepository;
import com.fixflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PresenceService {

    public record HeartbeatRequest(
            String deviceId,
            String platform,
            String appVersion,
            Integer nativeBuild
    ) {
    }

    private final PresenceStore presenceStore;
    private final UserRepository userRepository;
    private final ShopRepository shopRepository;

    public PresenceSnapshot heartbeat(HeartbeatRequest request, String ipAddress) {
        UserPrincipal principal = CurrentUser.require();
        var user = userRepository.findById(principal.getId()).orElseThrow();
        UUID shopId = principal.getShopId();
        String shopName = shopId == null ? null
                : shopRepository.findById(shopId).map(shop -> shop.getName()).orElse(null);
        String deviceId = request == null || request.deviceId() == null || request.deviceId().isBlank()
                ? "unknown"
                : request.deviceId().trim();
        PresenceSnapshot snapshot = new PresenceSnapshot(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                shopId,
                shopName,
                deviceId,
                request == null || request.platform() == null ? "WEB" : request.platform(),
                request == null ? null : request.appVersion(),
                request == null ? null : request.nativeBuild(),
                ipAddress,
                Instant.now());
        presenceStore.heartbeat(snapshot);
        return snapshot;
    }

    public List<PresenceSnapshot> liveForWorkspace(UUID shopId) {
        return presenceStore.listLive().stream()
                .filter(row -> shopId != null && shopId.equals(row.shopId()))
                .toList();
    }

    public List<PresenceSnapshot> liveAll() {
        return presenceStore.listLive();
    }

    public void leave(String deviceId) {
        presenceStore.leave(CurrentUser.userId().toString(), deviceId == null ? "unknown" : deviceId);
    }

    /**
     * Drops a live session so the floor listing no longer shows them. Does not revoke tokens —
     * that is Identity's, through the oneOps break-glass path.
     */
    public int kick(UUID userId, String deviceId) {
        if (deviceId != null && !deviceId.isBlank()) {
            presenceStore.leave(userId.toString(), deviceId.trim());
            return 1;
        }
        int live = (int) liveAll().stream().filter(row -> userId.equals(row.userId())).count();
        presenceStore.kick(userId.toString());
        return live;
    }
}
