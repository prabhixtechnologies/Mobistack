package com.fixflow.security;

import com.fixflow.billing.service.BillingService;
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
import java.util.UUID;

/**
 * Catalog-only plans may use billing, workspaces, the shared fitment catalog and support paths.
 * Full shop APIs stay available on paid operational plans — this filter never blocks them.
 */
@Component
@RequiredArgsConstructor
public class CatalogOnlyFilter extends OncePerRequestFilter {

    private final BillingService billingService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (allowed(request) || !signedIn()) {
            filterChain.doFilter(request, response);
            return;
        }
        UserPrincipal principal = (UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        UUID shopId = principal.getShopId();
        if (shopId == null || !billingService.catalogOnly(shopId) || billingService.callerIsSystemAdmin()) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(ErrorCode.ENTITLEMENT_DENIED.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                ApiError.of(ErrorCode.ENTITLEMENT_DENIED,
                        "This plan includes the fitment catalog only. Open Billing to upgrade for shop operations.",
                        request.getRequestURI()));
    }

    static boolean allowed(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        if (!path.startsWith("/api/v1/mobistack/")) {
            return true;
        }
        if (path.startsWith("/api/v1/mobistack/auth")
                || path.startsWith("/api/v1/mobistack/billing")
                || path.startsWith("/api/v1/mobistack/public")
                || path.startsWith("/api/v1/mobistack/admin")
                || path.startsWith("/api/v1/mobistack/groups")
                || path.startsWith("/api/v1/mobistack/commons")
                || path.startsWith("/api/v1/mobistack/workspaces")
                || path.startsWith("/api/v1/mobistack/notifications")
                || path.startsWith("/api/v1/mobistack/support")
                || path.startsWith("/api/v1/mobistack/feature-flags")
                || path.startsWith("/api/v1/mobistack/invitations")
                || path.startsWith("/api/v1/mobistack/shop")
                || path.startsWith("/api/v1/mobistack/presence")
                || path.startsWith("/api/v1/mobistack/inbox")) {
            return true;
        }
        return "POST".equals(method) && "/api/v1/mobistack/workspaces/select".equals(path);
    }

    private static boolean signedIn() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
                && authentication.getPrincipal() instanceof UserPrincipal;
    }
}
