package com.fixflow.presence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

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
        } catch (JsonProcessingException ex) {
            log.warn("Could not write presence to Redis: {}", ex.getMessage());
        }
    }

    @Override
    public List<PresenceSnapshot> listLive() {
        Set<String> keys = redis.keys(KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        List<PresenceSnapshot> rows = new ArrayList<>();
        for (String redisKey : keys) {
            String raw = redis.opsForValue().get(redisKey);
            if (raw == null) {
                continue;
            }
            try {
                rows.add(objectMapper.readValue(raw, PresenceSnapshot.class));
            } catch (JsonProcessingException ex) {
                log.debug("Skipping malformed presence key {}", redisKey);
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
