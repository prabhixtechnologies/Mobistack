package com.fixflow.common.web;

import com.fixflow.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;

public final class ClientRequests {

    public static final String DEVICE_HEADER = "X-FixFlow-Device";

    private ClientRequests() {
    }

    public static AuthService.ClientInfo clientInfo(HttpServletRequest http, String deviceId) {
        String resolved = deviceId == null || deviceId.isBlank() ? http.getHeader(DEVICE_HEADER) : deviceId;
        String forwarded = http.getHeader("X-Forwarded-For");
        String ip = forwarded == null || forwarded.isBlank()
                ? http.getRemoteAddr()
                : forwarded.split(",")[0].trim();
        return new AuthService.ClientInfo(resolved, http.getHeader(HttpHeaders.USER_AGENT), ip);
    }

    public static String ip(HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) {
            return http.getRemoteAddr();
        }
        return forwarded.split(",")[0].trim();
    }
}
