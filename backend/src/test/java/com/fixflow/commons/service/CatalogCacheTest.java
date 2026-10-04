package com.fixflow.commons.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;
import java.util.concurrent.ConcurrentMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CatalogCacheTest {

    private ConcurrentMapCacheManager manager;
    private CatalogCache cache;

    @BeforeEach
    void setUp() {
        manager = new ConcurrentMapCacheManager(CatalogCache.NAME);
        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> redis = mock(ObjectProvider.class);
        cache = new CatalogCache(manager, redis);
    }

    @Test
    void evictGroupDropsOnlyThatGroupsKeys() {
        UUID group = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        ConcurrentMapCache nativeCache = (ConcurrentMapCache) manager.getCache(CatalogCache.NAME);
        nativeCache.put(group + ":brands", "a");
        nativeCache.put(group + ":family:" + UUID.randomUUID(), "b");
        nativeCache.put(other + ":brands", "c");

        cache.evictGroup(group);

        ConcurrentMap<Object, Object> map = nativeCache.getNativeCache();
        assertThat(map.keySet().stream().map(String::valueOf))
                .noneMatch(key -> key.contains(group.toString()))
                .anyMatch(key -> key.contains(other.toString()));
    }
}
