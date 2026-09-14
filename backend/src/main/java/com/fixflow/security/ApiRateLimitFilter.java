package com.fixflow.security;

import tools.jackson.databind.ObjectMapper;
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
 * Per-IP throttle for the API. Uses Redis when the cluster is configured so nodes share the same
 * counters.
 *
 * <p>There is no longer a tighter bucket for {@code /api/v1/auth/**}: sign-in, OTP and password
 * reset live on Prabhix Identity, which throttles them itself, and what remains under that prefix
 * ({@code /me}, {@code /logout}) is ordinary authenticated traffic.
 */
@Slf4j
@Component
public class ApiRateLimitFilter extends OncePerRequestFilter {

    private static final int API_LIMIT = 240;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final ObjectMapper objectMapper;
    private final FixFlowProperties properties;
    private final ObjectProvider<StringRedisTemplate> redis;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public ApiRateLimitFilter(ObjectMapper objectMapper, FixFlowProperties properties,
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
        String key = "api:" + clientKey(request);
        if (overLimit(key, API_LIMIT)) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(),
                    ApiError.of(ErrorCode.BUSINESS_RULE_VIOLATION,
                            "Too many requests. Try again shortly.",
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
