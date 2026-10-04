package com.fixflow.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import tools.jackson.databind.DefaultTyping;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;
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
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
@EnableCaching
public class PlatformCacheConfig {

    @Bean
    @Primary
    @ConditionalOnProperty(name = "fixflow.redis.enabled", havingValue = "false", matchIfMissing = true)
    public CacheManager simpleCacheManager() {
        return new ConcurrentMapCacheManager("dashboard", "flags", "platform", "app-release", "catalog");
    }

    @Bean
    @ConditionalOnProperty(name = "fixflow.redis.enabled", havingValue = "true")
    public CacheManager redisCacheManager(RedisConnectionFactory factory, FixFlowProperties properties,
                                         ObjectMapper objectMapper) {
        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                // The unsuffixed serializer is the Jackson 3 one; GenericJackson2JsonRedisSerializer
                // is deprecated alongside Jackson 2 itself. Cache entries are disposable.
                //
                // The API mapper writes records as plain objects. Read back through the cache, which
                // only knows Object, those become maps, and a list of catalog rows then fails the
                // cast in the controller — the page stays blank while the counts, which are not
                // cached this way, still load. A copy of the mapper adds type names for records.
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(redisSerializer(objectMapper)))
                .disableCachingNullValues()
                .entryTtl(properties.getRedis().getDashboardTtl());
        return RedisCacheManager.builder(factory)
                .cacheDefaults(base)
                .withCacheConfiguration("flags", base.entryTtl(Duration.ofMinutes(2)))
                .withCacheConfiguration("app-release", base.entryTtl(Duration.ofSeconds(30)))
                .withCacheConfiguration("platform", base.entryTtl(Duration.ofMinutes(5)))
                .withCacheConfiguration("catalog", base.entryTtl(Duration.ofMinutes(10)))
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

    /**
     * Cache values have to round-trip as the class that was stored. The API mapper does not write
     * type names, which is correct for responses and wrong for a cache that reads them as Object.
     */
    static RedisSerializer<Object> redisSerializer(ObjectMapper objectMapper) {
        PolymorphicTypeValidator types = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.fixflow.")
                .allowIfSubType("java.util.")
                .allowIfSubType("java.time.")
                .build();
        ObjectMapper typed = objectMapper.rebuild()
                .activateDefaultTyping(types, DefaultTyping.NON_FINAL_AND_RECORDS, JsonTypeInfo.As.PROPERTY)
                .build();
        return new GenericJacksonJsonRedisSerializer(typed);
    }
}
