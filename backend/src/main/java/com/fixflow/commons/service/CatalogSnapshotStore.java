package com.fixflow.commons.service;

import com.fixflow.common.error.ApiException;
import com.fixflow.common.error.ErrorCode;
import com.fixflow.config.FixFlowProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Published catalog versions. Readers follow {@code current}. A refresh writes the next version
 * completely, then moves the pointer, and only then deletes anything older than the three kept
 * copies. A request never sees a half-written catalog, and it never waits on Postgres.
 */
@Slf4j
@Component
public class CatalogSnapshotStore {

    static final int KEPT_VERSIONS = 3;
    static final Duration RETENTION = Duration.ofHours(24);
    private static final Duration LOCK_TTL = Duration.ofMinutes(15);
    private static final String CURRENT = "catalog-snapshot:current";
    private static final String VERSIONS = "catalog-snapshot:versions";
    private static final String LOCK = "catalog-snapshot:lock";

    private static final DefaultRedisScript<Long> UNLOCK = new DefaultRedisScript<>("""
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            else
                return 0
            end
            """, Long.class);

    private final FixFlowProperties properties;
    private final ObjectProvider<StringRedisTemplate> redis;
    private final ObjectMapper objectMapper;
    private volatile Published published;

    public CatalogSnapshotStore(FixFlowProperties properties,
                                ObjectProvider<StringRedisTemplate> redis,
                                ObjectMapper objectMapper) {
        this.properties = properties;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    /**
     * Empty when Redis is off, so local runs and tests keep reading the database. When Redis is on,
     * a missing snapshot is a loading error rather than a database query.
     */
    public Optional<CatalogGroupSnapshot> serving(UUID groupId) {
        if (groupId == null || !properties.getRedis().isEnabled()) {
            return Optional.empty();
        }
        StringRedisTemplate template = redis.getIfAvailable();
        if (template == null) {
            throw new ApiException(ErrorCode.PROVIDER_UNAVAILABLE, "The catalog cache is unavailable.");
        }
        try {
            return Optional.of(snapshot(groupId, template));
        } catch (ApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("Catalog snapshot could not be read");
            throw new ApiException(ErrorCode.PROVIDER_UNAVAILABLE, "The catalog cache is unavailable.");
        }
    }

    /**
     * The same immutable version request threads are serving, for a phone to keep offline.
     *
     * <p>The client sends the version it already has. An unchanged response carries no document,
     * which makes an ordinary online launch a tiny request. A changed version is read from Redis;
     * PostgreSQL is never involved and a half-published version cannot be observed.
     */
    public CatalogDownload download(UUID groupId, String knownVersion) {
        if (groupId == null) {
            throw new ApiException(ErrorCode.PROVIDER_UNAVAILABLE, "No fitment group is selected.");
        }
        StringRedisTemplate template = requiredTemplate();
        String version = template.opsForValue().get(CURRENT);
        if (version == null || version.isBlank()) {
            throw new ApiException(ErrorCode.PROVIDER_UNAVAILABLE, "The catalog is still loading.");
        }
        if (version.equals(knownVersion)) {
            return new CatalogDownload(version, true, null);
        }
        String payload = template.opsForValue().get(groupKey(version, groupId));
        CatalogSnapshotDocument document = payload == null
                ? new CatalogSnapshotDocument(List.of(), List.of(), List.of(), List.of())
                : decode(payload);
        return new CatalogDownload(version, false, document);
    }

    public boolean tryLock(String token) {
        StringRedisTemplate template = requiredTemplate();
        return Boolean.TRUE.equals(template.opsForValue().setIfAbsent(LOCK, token, LOCK_TTL));
    }

    public void unlock(String token) {
        StringRedisTemplate template = redis.getIfAvailable();
        if (template == null || token == null) {
            return;
        }
        template.execute(UNLOCK, List.of(LOCK), token);
    }

    /**
     * Writes every group, then points readers at the new version, then drops versions beyond the
     * three most recent. The previous pointer keeps answering until that switch.
     */
    public void publish(Map<UUID, CatalogSnapshotDocument> documents) {
        StringRedisTemplate template = requiredTemplate();
        Map<UUID, CatalogSnapshotDocument> rows = documents == null ? Map.of() : documents;
        String version = Long.toString(System.currentTimeMillis());
        try {
            for (Map.Entry<UUID, CatalogSnapshotDocument> entry : rows.entrySet()) {
                template.opsForValue().set(groupKey(version, entry.getKey()), encode(entry.getValue()), RETENTION);
            }
            template.opsForValue().set(manifestKey(version),
                    objectMapper.writeValueAsString(new ArrayList<>(rows.keySet())), RETENTION);
        } catch (JacksonException | IOException ex) {
            throw new IllegalStateException("Catalog snapshot could not be stored", ex);
        }
        template.opsForList().leftPush(VERSIONS, version);
        List<String> drop = versionsToDrop(template.opsForList().range(VERSIONS, 0, -1));
        if (!drop.isEmpty()) {
            template.opsForList().trim(VERSIONS, 0, KEPT_VERSIONS - 1);
        }
        template.expire(VERSIONS, RETENTION);
        template.opsForValue().set(CURRENT, version, RETENTION);
        install(version, rows);
        for (String old : drop) {
            deleteVersion(template, old);
        }
    }

    static List<String> versionsToDrop(List<String> newestFirst) {
        if (newestFirst == null || newestFirst.size() <= KEPT_VERSIONS) {
            return List.of();
        }
        return List.copyOf(newestFirst.subList(KEPT_VERSIONS, newestFirst.size()));
    }

    private CatalogGroupSnapshot snapshot(UUID groupId, StringRedisTemplate template) {
        String version = template.opsForValue().get(CURRENT);
        if (version == null || version.isBlank()) {
            throw new ApiException(ErrorCode.PROVIDER_UNAVAILABLE, "The catalog is still loading.");
        }
        Published local = published;
        if (local == null || !local.version.equals(version)) {
            local = loadPublished(template, version);
        }
        return local.groups.getOrDefault(groupId, CatalogGroupSnapshot.empty(groupId));
    }

    private Published loadPublished(StringRedisTemplate template, String version) {
        synchronized (this) {
            Published local = published;
            if (local != null && local.version.equals(version)) {
                return local;
            }
            try {
                Published loaded = read(template, version);
                published = loaded;
                return loaded;
            } catch (RuntimeException ex) {
                if (published != null) {
                    log.warn("Catalog snapshot {} could not be read; still serving {}", version, published.version);
                    return published;
                }
                log.warn("Catalog snapshot {} could not be read", version);
                throw new ApiException(ErrorCode.PROVIDER_UNAVAILABLE, "The catalog cache is unavailable.");
            }
        }
    }

    private Published read(StringRedisTemplate template, String version) {
        String manifest = template.opsForValue().get(manifestKey(version));
        if (manifest == null) {
            throw new IllegalStateException("Catalog snapshot " + version + " has no manifest");
        }
        List<UUID> groupIds;
        try {
            groupIds = objectMapper.readValue(manifest, new TypeReference<List<UUID>>() {
            });
        } catch (JacksonException ex) {
            throw new IllegalStateException("Catalog snapshot manifest is unreadable", ex);
        }
        Map<UUID, CatalogGroupSnapshot> groups = new LinkedHashMap<>();
        for (UUID groupId : groupIds) {
            String payload = template.opsForValue().get(groupKey(version, groupId));
            if (payload == null) {
                groups.put(groupId, CatalogGroupSnapshot.empty(groupId));
                continue;
            }
            groups.put(groupId, CatalogGroupSnapshot.from(groupId, decode(payload)));
        }
        return new Published(version, Map.copyOf(groups));
    }

    private void install(String version, Map<UUID, CatalogSnapshotDocument> documents) {
        Map<UUID, CatalogGroupSnapshot> groups = new LinkedHashMap<>();
        documents.forEach((groupId, document) -> groups.put(groupId, CatalogGroupSnapshot.from(groupId, document)));
        published = new Published(version, Map.copyOf(groups));
    }

    private void deleteVersion(StringRedisTemplate template, String version) {
        String manifest = template.opsForValue().get(manifestKey(version));
        List<String> keys = new ArrayList<>();
        keys.add(manifestKey(version));
        if (manifest != null) {
            try {
                for (UUID groupId : objectMapper.readValue(manifest, new TypeReference<List<UUID>>() {
                })) {
                    keys.add(groupKey(version, groupId));
                }
            } catch (JacksonException ex) {
                log.warn("Could not list keys for catalog snapshot {}", version);
            }
        }
        template.delete(keys);
    }

    private StringRedisTemplate requiredTemplate() {
        StringRedisTemplate template = redis.getIfAvailable();
        if (template == null) {
            throw new IllegalStateException("Redis is required to publish the catalog snapshot");
        }
        return template;
    }

    private String encode(CatalogSnapshotDocument document) throws JacksonException, IOException {
        byte[] json = objectMapper.writeValueAsBytes(document);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(json);
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    private CatalogSnapshotDocument decode(String payload) {
        try {
            byte[] compressed = Base64.getDecoder().decode(payload);
            ByteArrayOutputStream json = new ByteArrayOutputStream();
            try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
                gzip.transferTo(json);
            }
            return objectMapper.readValue(json.toString(StandardCharsets.UTF_8), CatalogSnapshotDocument.class);
        } catch (JacksonException | IOException | IllegalArgumentException ex) {
            throw new IllegalStateException("Catalog snapshot payload is unreadable", ex);
        }
    }

    private static String manifestKey(String version) {
        return "catalog-snapshot:" + version + ":manifest";
    }

    private static String groupKey(String version, UUID groupId) {
        return "catalog-snapshot:" + version + ":" + groupId;
    }

    private record Published(String version, Map<UUID, CatalogGroupSnapshot> groups) {
    }

    public record CatalogDownload(String version,
                                  boolean unchanged,
                                  CatalogSnapshotDocument catalog) {
    }
}
