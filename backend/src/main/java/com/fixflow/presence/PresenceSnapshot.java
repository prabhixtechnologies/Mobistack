package com.fixflow.presence;

import java.time.Instant;
import java.util.UUID;

public record PresenceSnapshot(
        UUID userId,
        String fullName,
        String email,
        UUID shopId,
        String shopName,
        String deviceId,
        String platform,
        String appVersion,
        Integer nativeBuild,
        String ipAddress,
        Instant seenAt
) {
}
