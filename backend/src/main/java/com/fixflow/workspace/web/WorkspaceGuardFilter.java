package com.fixflow.workspace.web;

import tools.jackson.databind.ObjectMapper;
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
 * sent by the client that does not match the token, and refuses writes from a
 * workspace that has not paid.
 */
@Component
@Order(20)
@RequiredArgsConstructor
public class WorkspaceGuardFilter extends OncePerRequestFilter {

    public static final String WORKSPACE_HEADER = "X-MobiStack-Workspace";
    public static final String WORKSPACE_HEADER_LEGACY = "X-FixFlow-Workspace";

    /**
     * What an unpaid shop is still allowed to change. Everything here is either
     * how they pay, or something they need in order to pay: the plan itself,
     * their own shop details, which workspace they are in, and how we contact
     * them. Blocking these would leave a shop unable to buy its way out.
     */
    private static final String[] WRITABLE_WHILE_UNPAID = {
            "/api/v1/mobistack/billing",
            "/api/v1/mobistack/workspaces",
            "/api/v1/mobistack/shop",
            "/api/v1/mobistack/notifications",
            "/api/v1/mobistack/groups",
    };

    private final WorkspaceAccessService workspaceAccessService;
    private final com.fixflow.billing.service.BillingService billingService;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = pathOf(request);
        if (!path.startsWith("/api/")) {
            return true;
        }
        return path.startsWith("/api/v1/mobistack/auth")
                || path.startsWith("/api/v1/mobistack/public")
                || path.startsWith("/api/v1/mobistack/invitations")
                || path.startsWith("/api/v1/mobistack/billing/webhooks")
                || path.startsWith("/api/v1/mobistack/admin")
                || path.startsWith("/api/v1/mobistack/support")
                || path.startsWith("/api/v1/mobistack/presence")
                || path.startsWith("/api/v1/mobistack/inbox")
                // The shared compatibility catalog belongs to no shop, so requiring a selected
                // workspace would be asking which tenant owns a fact that is true for everyone. It is
                // also the free half of the product: somebody looks up what fits before they have a
                // shop at all, and an unpaid shop can still contribute — which is why this exemption
                // covers writes too, and is the only one here that does so deliberately.
                || path.startsWith("/api/v1/mobistack/commons");
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
            rejectUnpaidWrite(path, request.getMethod(), principal.getShopId());
            filterChain.doFilter(request, response);
        } catch (ApiException ex) {
            writeError(request, response, ex);
        }
    }

    /**
     * One gate instead of a billing check in every service. Reads stay open so
     * the app can still render and explain itself, but nothing new is recorded
     * in a shop that has not paid — which is also the rule for adding people.
     */
    private void rejectUnpaidWrite(String path, String method, UUID workspaceId) {
        if (!isMutation(method)) {
            return;
        }
        for (String allowed : WRITABLE_WHILE_UNPAID) {
            if (path.startsWith(allowed)) {
                return;
            }
        }
        if (billingService.paymentRequired(workspaceId)) {
            throw new ApiException(ErrorCode.ENTITLEMENT_DENIED,
                    "This shop has no active plan, so changes cannot be saved. "
                            + "Open Billing and complete payment to start working.");
        }
    }

    private static boolean isMutation(String method) {
        return "POST".equals(method) || "PUT".equals(method)
                || "PATCH".equals(method) || "DELETE".equals(method);
    }

    /**
     * List / create / join / select do not need a selected workspace.
     * Member listing does — it is a business call on a specific workspace.
     */
    private boolean isWorkspaceDirectory(String path, String method) {
        if ("/api/v1/mobistack/workspaces".equals(path) && ("GET".equals(method) || "POST".equals(method))) {
            return true;
        }
        if (path.startsWith("/api/v1/mobistack/workspaces/join") && "POST".equals(method)) {
            return true;
        }
        if ("/api/v1/mobistack/workspaces/select".equals(path) && "POST".equals(method)) {
            return true;
        }
        return "/api/v1/mobistack/workspaces/join/cancel".equals(path) && "POST".equals(method);
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
            header = request.getHeader(WORKSPACE_HEADER_LEGACY);
        }
        if (header == null || header.isBlank()) {
            return;
        }
        UUID requested;
        try {
            requested = UUID.fromString(header.trim());
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.MALFORMED_REQUEST, "X-MobiStack-Workspace is not a UUID.");
        }
        if (principal.getShopId() == null || !requested.equals(principal.getShopId())) {
            throw new ApiException(ErrorCode.FORBIDDEN,
                    "Workspace switch requires POST /api/v1/mobistack/workspaces/select?id=.");
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
