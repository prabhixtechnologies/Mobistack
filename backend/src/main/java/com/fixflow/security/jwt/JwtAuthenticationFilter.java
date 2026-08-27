package com.fixflow.security.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixflow.auth.service.DeviceSessionService;
import com.fixflow.common.error.ApiError;
import com.fixflow.common.error.ApiException;
import com.fixflow.common.error.ErrorCode;
import com.fixflow.security.UserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final DeviceSessionService deviceSessionService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();
        try {
            UserPrincipal principal = jwtService.parseAccessToken(token);
            String deviceId = jwtService.deviceIdFrom(token);
            if (deviceId != null && !deviceSessionService.isLive(principal.getId(), deviceId)) {
                throw new ApiException(ErrorCode.SESSION_REPLACED,
                        "This device's session has ended. Sign in again to continue.");
            }
            var authentication = new UsernamePasswordAuthenticationToken(
                    principal, null, principal.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (ApiException ex) {
            SecurityContextHolder.clearContext();
            // Sign-in and other anonymous routes must still run when the browser
            // still has an expired or replaced access token in localStorage.
            if (isAnonymousOk(request)) {
                filterChain.doFilter(request, response);
                return;
            }
            writeError(request, response, ex);
            return;
        }

        filterChain.doFilter(request, response);
    }

    static boolean isAnonymousOk(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) {
            return false;
        }
        if (path.startsWith("/api/v1/public/") || path.startsWith("/download/")
                || path.startsWith("/actuator/health")) {
            return true;
        }
        if (!path.startsWith("/api/v1/auth/")) {
            return false;
        }
        return path.equals("/api/v1/auth/login")
                || path.equals("/api/v1/auth/refresh")
                || path.equals("/api/v1/auth/register")
                || path.equals("/api/v1/auth/register-shop")
                || path.equals("/api/v1/auth/forgot-password")
                || path.equals("/api/v1/auth/reset-password")
                || path.equals("/api/v1/auth/request-otp")
                || path.equals("/api/v1/auth/verify-otp")
                || path.equals("/api/v1/auth/methods")
                || path.equals("/api/v1/auth/magic-link")
                || path.equals("/api/v1/auth/magic-link/consume")
                || path.equals("/api/v1/auth/email-otp")
                || path.equals("/api/v1/auth/email-otp/verify")
                || path.equals("/api/v1/auth/phone/start")
                || path.equals("/api/v1/auth/phone/verify")
                || path.equals("/api/v1/auth/whatsapp/start")
                || path.equals("/api/v1/auth/whatsapp/verify")
                || path.equals("/api/v1/auth/sso/google/start")
                || path.equals("/api/v1/auth/sso/google")
                || path.equals("/api/v1/auth/sso/dev");
    }

    private void writeError(HttpServletRequest request, HttpServletResponse response, ApiException ex)
            throws IOException {
        response.setStatus(ex.getCode().status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(),
                ApiError.of(ex.getCode(), ex.getMessage(), request.getRequestURI()));
    }
}
