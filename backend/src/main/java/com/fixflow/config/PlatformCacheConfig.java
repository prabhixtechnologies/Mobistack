package com.fixflow.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixflow.presence.MemoryPresenceStore;
import com.fixflow.presence.PresenceStore;
import com.fixflow.presence.RedisPresenceStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
@EnableCaching
public class PlatformCacheConfig {

    @Bean
    @Primary
    @ConditionalOnProperty(name = "fixflow.redis.enabled", havingValue = "false", matchIfMissing = true)
    public CacheManager simpleCacheManager() {
        return new ConcurrentMapCacheManager("dashboard", "flags", "platform", "app-release");
    }

    @Bean
    @ConditionalOnProperty(name = "fixflow.redis.enabled", havingValue = "true")
    public CacheManager redisCacheManager(RedisConnectionFactory factory, FixFlowProperties properties) {
        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues()
                .entryTtl(properties.getRedis().getDashboardTtl());
        return RedisCacheManager.builder(factory)
                .cacheDefaults(base)
                .withCacheConfiguration("flags", base.entryTtl(Duration.ofMinutes(2)))
                .withCacheConfiguration("app-release", base.entryTtl(Duration.ofSeconds(30)))
                .withCacheConfiguration("platform", base.entryTtl(Duration.ofMinutes(5)))
                .build();
    }

    @Bean
    public PresenceStore presenceStore(FixFlowProperties properties,
                                       ObjectProvider<StringRedisTemplate> redis,
                                       ObjectMapper objectMapper) {
        Duration ttl = properties.getRedis().getPresenceTtl();
        StringRedisTemplate template = properties.getRedis().isEnabled() ? redis.getIfAvailable() : null;
        if (template != null) {
            return new RedisPresenceStore(template, objectMapper, ttl);
        }
        return new MemoryPresenceStore(ttl);
    }
}
