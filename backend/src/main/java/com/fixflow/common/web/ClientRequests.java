package com.fixflow.common.web;

import jakarta.servlet.http.HttpServletRequest;

public final class ClientRequests {

    public static final String DEVICE_HEADER = "X-MobiStack-Device";
    public static final String DEVICE_HEADER_LEGACY = "X-FixFlow-Device";
    public static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private ClientRequests() {
    }

    /** The calling device, or null when the client did not identify itself. */
    public static String deviceId(HttpServletRequest http) {
        return firstHeader(http, DEVICE_HEADER, DEVICE_HEADER_LEGACY);
    }

    /**
     * Caller-supplied key that makes a retried write land once.
     *
     * <p>Sent as a header rather than in the body so it works the same whichever
     * endpoint is being retried, and so a proxy replaying a request cannot lose
     * it by rewriting the payload.
     */
    public static String idempotencyKey(HttpServletRequest http) {
        return firstHeader(http, IDEMPOTENCY_HEADER);
    }

    public static String ip(HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) {
            return http.getRemoteAddr();
        }
        return forwarded.split(",")[0].trim();
    }

    private static String firstHeader(HttpServletRequest http, String... names) {
        for (String name : names) {
            String value = http.getHeader(name);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
