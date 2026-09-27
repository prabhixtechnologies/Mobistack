package com.fixflow.security;

import com.prabhix.identity.client.ServiceTokenGuard;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Service-to-service routes under {@code /internal/} are never anonymous. The shared product token
 * is the only credential; a shop bearer is ignored on this prefix.
 */
@Component
@RequiredArgsConstructor
public class InternalServiceAuthFilter extends OncePerRequestFilter {

    private static final String PREFIX = "/internal/";

    private static final String ROUTE = "internal";

    private final ServiceTokenGuard serviceToken;
    private final ServiceTokenAuthHandler authHandler;

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith(PREFIX);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (!authHandler.permitOrReject(request, response, serviceToken, ROUTE)) {
            return;
        }
        filterChain.doFilter(request, response);
    }
}
