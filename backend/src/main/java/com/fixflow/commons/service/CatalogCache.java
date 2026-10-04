package com.fixflow.commons.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;

/**
 * Shared catalog reads. Keys always include the fitment group, and a write evicts that group after
 * commit. Shop stock is not stored here.
 */
@Component
public class CatalogCache {

    public static final String NAME = "catalog";

    private final CacheManager cacheManager;
    private final ObjectProvider<StringRedisTemplate> redis;

    public CatalogCache(CacheManager cacheManager, ObjectProvider<StringRedisTemplate> redis) {
        this.cacheManager = cacheManager;
        this.redis = redis;
    }

    public void evictGroupAfterCommit(UUID groupId) {
        if (groupId == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evictGroup(groupId);
                }
            });
            return;
        }
        evictGroup(groupId);
    }

    public void evictGroup(UUID groupId) {
        if (groupId == null) {
            return;
        }
        Cache cache = cacheManager.getCache(NAME);
        if (cache == null) {
            return;
        }
        String needle = groupId.toString();
        Object nativeCache = cache.getNativeCache();
        if (nativeCache instanceof ConcurrentMap<?, ?> map) {
            List<Object> keys = new ArrayList<>();
            for (Object key : map.keySet()) {
                if (String.valueOf(key).contains(needle)) {
                    keys.add(key);
                }
            }
            keys.forEach(cache::evict);
            return;
        }
        StringRedisTemplate template = redis.getIfAvailable();
        if (template == null) {
            cache.clear();
            return;
        }
        try {
            Set<String> keys = template.keys(NAME + "::*" + needle + "*");
            if (keys != null && !keys.isEmpty()) {
                template.delete(keys);
            }
        } catch (RuntimeException ex) {
            cache.clear();
        }
    }
}
