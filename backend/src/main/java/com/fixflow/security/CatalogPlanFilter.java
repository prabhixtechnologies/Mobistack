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

/**
 * The fitment catalog is the ₹50 plan. A signed-in shop that has not paid does not
 * receive the phone list or the fitment rows.
 */
@Component
@RequiredArgsConstructor
public class CatalogPlanFilter extends OncePerRequestFilter {

    private final BillingService billingService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path == null || !path.startsWith("/api/v1/mobistack/commons") || !signedIn()) {
            filterChain.doFilter(request, response);
            return;
        }
        UserPrincipal principal = (UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal.getShopId() != null && billingService.hasCatalog(principal.getShopId())) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(ErrorCode.ENTITLEMENT_DENIED.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                ApiError.of(ErrorCode.ENTITLEMENT_DENIED,
                        "The fitment catalog is the ₹50 plan. Open Billing and pay to open it.",
                        path));
    }

    private static boolean signedIn() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
                && authentication.getPrincipal() instanceof UserPrincipal;
    }
}
