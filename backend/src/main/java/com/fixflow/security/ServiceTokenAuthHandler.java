package com.fixflow.security;

import tools.jackson.databind.ObjectMapper;
import com.fixflow.common.error.ApiError;
import com.fixflow.common.error.ErrorCode;
import com.fixflow.config.FixFlowProperties;
import com.prabhix.identity.client.ServiceTokenGuard;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared rejection path for {@code /internal/**} and {@code /api/v1/mobistack/admin/**}: rate-limit
 * failed service-token attempts and log an audit record for each rejection.
 */
@Slf4j
@Component
public class ServiceTokenAuthHandler {

    static final int FAILURE_LIMIT = 20;
    static final Duration WINDOW = Duration.ofMinutes(1);

    private final ObjectMapper objectMapper;
    private final FixFlowProperties properties;
    private final ObjectProvider<StringRedisTemplate> redis;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public ServiceTokenAuthHandler(ObjectMapper objectMapper, FixFlowProperties properties,
                                   ObjectProvider<StringRedisTemplate> redis) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.redis = redis;
    }

    /**
     * @return {@code true} when the caller may continue the filter chain
     */
    public boolean permitOrReject(HttpServletRequest request, HttpServletResponse response,
                                  ServiceTokenGuard guard, String route) throws IOException {
        if (guard.configured() && guard.permits(request)) {
            return true;
        }
        String client = clientKey(request);
        String bucket = "svc-token-fail:" + route + ":" + client;
        if (overLimit(bucket)) {
            log.warn("Service token attempts throttled route={} client={} path={}",
                    route, client, request.getRequestURI());
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(),
                    ApiError.of(ErrorCode.BUSINESS_RULE_VIOLATION,
                            "Too many invalid service token attempts. Try again shortly.",
                            request.getRequestURI()));
            return false;
        }
        auditRejection(route, client, request, guard.configured());
        String message = guard.configured()
                ? "That service token is not valid."
                : "Internal routes require a configured service token.";
        if ("platform-admin".equals(route)) {
            message = guard.configured()
                    ? "Platform admin requires the operations service token."
                    : message;
        }
        write(response, ErrorCode.UNAUTHENTICATED.status().value(), message, request.getRequestURI());
        return false;
    }

    private void auditRejection(String route, String client, HttpServletRequest request, boolean configured) {
        log.warn(
                "AUDIT service_token_rejected route={} client={} path={} method={} tokenConfigured={}",
                route,
                client,
                request.getRequestURI(),
                request.getMethod(),
                configured);
    }

    private void write(HttpServletResponse response, int status, String message, String path)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                ApiError.of(ErrorCode.UNAUTHENTICATED, message, path));
    }

    private boolean overLimit(String key) {
        StringRedisTemplate template = properties.getRedis().isEnabled() ? redis.getIfAvailable() : null;
        if (template != null) {
            try {
                String redisKey = "rl:" + key;
                Long count = template.opsForValue().increment(redisKey);
                if (count != null && count == 1L) {
                    template.expire(redisKey, WINDOW);
                }
                return count != null && count > FAILURE_LIMIT;
            } catch (RuntimeException ex) {
                log.warn("Redis service-token rate limit unavailable, using local counters: {}", ex.getMessage());
            }
        }
        long now = Instant.now().toEpochMilli();
        long windowMs = WINDOW.toMillis();
        Deque<Long> stamps = hits.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (stamps) {
            while (!stamps.isEmpty() && now - stamps.peekFirst() > windowMs) {
                stamps.removeFirst();
            }
            if (stamps.size() >= FAILURE_LIMIT) {
                return true;
            }
            stamps.addLast(now);
            return false;
        }
    }

    private String clientKey(HttpServletRequest request) {
        String remote = request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
        if (trustsProxy(remote)) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return remote;
    }

    private boolean trustsProxy(String remoteAddr) {
        for (String trusted : properties.getPlatform().getTrustedProxies()) {
            if (trusted != null && !trusted.isBlank() && trusted.trim().equals(remoteAddr)) {
                return true;
            }
        }
        return false;
    }
}
