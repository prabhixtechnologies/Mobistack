package com.fixflow.workspace.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixflow.common.error.ApiError;
import com.fixflow.common.error.ApiException;
import com.fixflow.common.error.ErrorCode;
import com.fixflow.security.UserPrincipal;
import com.fixflow.workspace.service.WorkspaceAccessService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Re-checks membership on every business request so a suspended user cannot
 * keep working until their access token expires. Also rejects a workspace id
 * sent by the client that does not match the token.
 */
@Component
@Order(20)
@RequiredArgsConstructor
public class WorkspaceGuardFilter extends OncePerRequestFilter {

    public static final String WORKSPACE_HEADER = "X-FixFlow-Workspace";

    private final WorkspaceAccessService workspaceAccessService;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = pathOf(request);
        if (!path.startsWith("/api/")) {
            return true;
        }
        return path.startsWith("/api/v1/auth")
                || path.startsWith("/api/v1/public")
                || path.startsWith("/api/v1/invitations")
                || path.startsWith("/api/v1/billing/webhooks")
                || path.startsWith("/api/v1/admin")
                || path.startsWith("/api/v1/support")
                || path.startsWith("/api/v1/presence")
                || path.startsWith("/api/v1/inbox");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String path = pathOf(request);
            if (isWorkspaceDirectory(path, request.getMethod())) {
                filterChain.doFilter(request, response);
                return;
            }
            rejectMismatchedHeader(request, principal);
            if (!principal.hasWorkspace()) {
                throw new ApiException(ErrorCode.WORKSPACE_REQUIRED,
                        "Select a workspace before calling this endpoint.");
            }
            workspaceAccessService.requireActive(principal.getId(), principal.getShopId());
            filterChain.doFilter(request, response);
        } catch (ApiException ex) {
            writeError(request, response, ex);
        }
    }

    /**
     * List / create / join / select do not need a selected workspace.
     * Member listing does — it is a business call on a specific workspace.
     */
    private boolean isWorkspaceDirectory(String path, String method) {
        if ("/api/v1/workspaces".equals(path) && ("GET".equals(method) || "POST".equals(method))) {
            return true;
        }
        if ("/api/v1/workspaces/join".equals(path) && "POST".equals(method)) {
            return true;
        }
        return path.matches("/api/v1/workspaces/[0-9a-fA-F-]{36}/select") && "POST".equals(method);
    }

    private static String pathOf(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        if (servletPath != null && !servletPath.isBlank()) {
            return servletPath;
        }
        return request.getRequestURI();
    }

    private void rejectMismatchedHeader(HttpServletRequest request, UserPrincipal principal) {
        String header = request.getHeader(WORKSPACE_HEADER);
        if (header == null || header.isBlank()) {
            return;
        }
        UUID requested;
        try {
            requested = UUID.fromString(header.trim());
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.MALFORMED_REQUEST, "X-FixFlow-Workspace is not a UUID.");
        }
        if (principal.getShopId() == null || !requested.equals(principal.getShopId())) {
            throw new ApiException(ErrorCode.FORBIDDEN,
                    "Workspace switch requires POST /api/v1/workspaces/{id}/select.");
        }
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
