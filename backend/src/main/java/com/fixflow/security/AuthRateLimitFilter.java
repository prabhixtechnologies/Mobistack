package com.fixflow.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixflow.common.error.ApiError;
import com.fixflow.common.error.ErrorCode;
import com.fixflow.config.FixFlowProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-IP throttle for public and authenticated API routes. Uses Redis when the
 * cluster is configured so nodes share the same counters.
 */
@Slf4j
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final int AUTH_LIMIT = 40;
    private static final int API_LIMIT = 240;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final ObjectMapper objectMapper;
    private final FixFlowProperties properties;
    private final ObjectProvider<StringRedisTemplate> redis;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public AuthRateLimitFilter(ObjectMapper objectMapper, FixFlowProperties properties,
                               ObjectProvider<StringRedisTemplate> redis) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.redis = redis;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null || !path.startsWith("/api/")) {
            return true;
        }
        return path.startsWith("/actuator/") || path.startsWith("/v3/api-docs") || path.startsWith("/swagger-ui");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        boolean auth = path != null && path.startsWith("/api/v1/auth/");
        int limit = auth ? AUTH_LIMIT : API_LIMIT;
        String key = (auth ? "auth:" : "api:") + clientKey(request);
        if (overLimit(key, limit)) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(),
                    ApiError.of(ErrorCode.BUSINESS_RULE_VIOLATION,
                            auth ? "Too many sign-in attempts. Try again shortly."
                                    : "Too many requests. Try again shortly.",
                            request.getRequestURI()));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean overLimit(String key, int limit) {
        StringRedisTemplate template = properties.getRedis().isEnabled() ? redis.getIfAvailable() : null;
        if (template != null) {
            try {
                String redisKey = "rl:" + key;
                Long count = template.opsForValue().increment(redisKey);
                if (count != null && count == 1L) {
                    template.expire(redisKey, WINDOW);
                }
                return count != null && count > limit;
            } catch (RuntimeException ex) {
                log.warn("Redis rate limit unavailable, using local counters: {}", ex.getMessage());
            }
        }
        long now = Instant.now().toEpochMilli();
        long windowMs = WINDOW.toMillis();
        Deque<Long> stamps = hits.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (stamps) {
            while (!stamps.isEmpty() && now - stamps.peekFirst() > windowMs) {
                stamps.removeFirst();
            }
            if (stamps.size() >= limit) {
                return true;
            }
            stamps.addLast(now);
            return false;
        }
    }

    private static String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }
}
