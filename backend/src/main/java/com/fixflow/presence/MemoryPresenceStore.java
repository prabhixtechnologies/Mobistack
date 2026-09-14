package com.fixflow.presence;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class MemoryPresenceStore implements PresenceStore {

    private final ConcurrentHashMap<String, PresenceSnapshot> live = new ConcurrentHashMap<>();
    private final Duration ttl;

    public MemoryPresenceStore(Duration ttl) {
        this.ttl = ttl;
    }

    @Override
    public void heartbeat(PresenceSnapshot snapshot) {
        live.put(key(snapshot.userId().toString(), snapshot.deviceId()), snapshot);
    }

    @Override
    public List<PresenceSnapshot> listLive() {
        Instant cutoff = Instant.now().minus(ttl);
        live.entrySet().removeIf(entry -> entry.getValue().seenAt().isBefore(cutoff));
        return live.values().stream()
                .sorted(Comparator.comparing(PresenceSnapshot::seenAt).reversed())
                .toList();
    }

    @Override
    public void leave(String userId, String deviceId) {
        live.remove(key(userId, deviceId));
    }

    @Override
    public void kick(String userId) {
        String prefix = userId + ":";
        live.keySet().removeIf(key -> key.startsWith(prefix));
    }


    private static String key(String userId, String deviceId) {
        return userId + ":" + deviceId;
    }
}
