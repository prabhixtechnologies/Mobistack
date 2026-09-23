package com.fixflow.security;

import com.fixflow.common.error.ApiError;
import com.fixflow.common.error.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Shop operations are closed. A signed-in caller may only read the shared
 * fitment catalog, pay for the workspace, and keep the session that billing needs.
 */
@Component
@RequiredArgsConstructor
public class CatalogOnlyFilter extends OncePerRequestFilter {

    private static final Pattern WORKSPACE_SELECT = Pattern.compile("^/api/v1/workspaces/[^/]+/select$");

    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (allowed(request) || !signedIn()) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(ErrorCode.FORBIDDEN.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                ApiError.of(ErrorCode.FORBIDDEN,
                        "Only the fitment catalog and billing are available.",
                        request.getRequestURI()));
    }

    static boolean allowed(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        if (!path.startsWith("/api/v1/")) {
            return true;
        }
        if (path.startsWith("/api/v1/auth")
                || path.startsWith("/api/v1/billing")
                || path.startsWith("/api/v1/public")
                || path.startsWith("/api/v1/admin")
                || path.startsWith("/api/v1/groups")) {
            return true;
        }
        if ("GET".equals(method) && path.startsWith("/api/v1/commons")) {
            return true;
        }
        if ("POST".equals(method) && "/api/v1/commons/contributions".equals(path)) {
            return true;
        }
        if ("GET".equals(method) && (path.equals("/api/v1/workspaces") || path.startsWith("/api/v1/workspaces/"))) {
            return true;
        }
        return "POST".equals(method) && WORKSPACE_SELECT.matcher(path).matches();
    }

    private static boolean signedIn() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }
}
