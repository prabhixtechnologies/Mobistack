package com.fixflow.security;

import com.prabhix.identity.client.ServiceTokenGuard;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Turns a BFF call into a principal for {@code /api/v1/mobistack/admin/**}.
 *
 * <p>The shared service token authenticates the product; {@code X-Prabhix-Acting-User} names the
 * staff member. A shop JWT is ignored on this prefix — {@link com.fixflow.security.jwt.JwtAuthenticationFilter}
 * skips it — so a counter user cannot reach platform admin by presenting their own bearer.
 */
@Component
@RequiredArgsConstructor
public class PlatformAdminAuthFilter extends OncePerRequestFilter {

    private static final String ADMIN_PREFIX = "/api/v1/mobistack/admin";

    private final ServiceTokenGuard serviceToken;

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) {
            return true;
        }
        return !(path.equals(ADMIN_PREFIX) || path.startsWith(ADMIN_PREFIX + "/"));
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (serviceToken.permits(request)) {
            UUID actor = serviceToken.actingUser(request).orElse(null);
            if (actor != null) {
                UserPrincipal principal = UserPrincipal.platformBff(actor);
                var authentication = new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        filterChain.doFilter(request, response);
    }
}
