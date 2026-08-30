package com.fixflow.presence;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class RedisPresenceStore implements PresenceStore {

    private static final String KEY_PREFIX = "fixflow:presence:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    @Override
    public void heartbeat(PresenceSnapshot snapshot) {
        try {
            redis.opsForValue().set(key(snapshot.userId().toString(), snapshot.deviceId()),
                    objectMapper.writeValueAsString(snapshot), ttl);
        } catch (JacksonException ex) {
            log.warn("Could not write presence to Redis: {}", ex.getMessage());
        }
    }

    /**
     * SCAN rather than KEYS: KEYS is O(keyspace) and blocks the server for the whole sweep, and
     * against a cluster it has to be fanned out to every shard. SCAN gives the same answer in
     * bounded slices.
     *
     * <p>Values are fetched one key at a time on purpose. MGET would be one round trip instead of
     * N, but a multi-key read against a cluster only works when every key hashes to the same slot,
     * and presence keys are spread across the keyspace by design.
     */
    @Override
    public List<PresenceSnapshot> listLive() {
        List<PresenceSnapshot> rows = new ArrayList<>();
        ScanOptions options = ScanOptions.scanOptions().match(KEY_PREFIX + "*").count(200).build();
        try (Cursor<String> cursor = redis.scan(options)) {
            while (cursor.hasNext()) {
                String redisKey = cursor.next();
                String raw = redis.opsForValue().get(redisKey);
                if (raw == null) {
                    continue;
                }
                try {
                    rows.add(objectMapper.readValue(raw, PresenceSnapshot.class));
                } catch (JacksonException ex) {
                    log.debug("Skipping malformed presence key {}", redisKey);
                }
            }
        }
        rows.sort(Comparator.comparing(PresenceSnapshot::seenAt).reversed());
        return rows;
    }

    @Override
    public void leave(String userId, String deviceId) {
        redis.delete(key(userId, deviceId));
    }

    private static String key(String userId, String deviceId) {
        return KEY_PREFIX + userId + ":" + deviceId;
    }
}
