package com.fixflow.security;

import tools.jackson.databind.ObjectMapper;
import com.fixflow.common.error.ApiError;
import com.fixflow.common.error.ErrorCode;
import com.fixflow.config.FixFlowProperties;
import com.fixflow.security.jwt.JwtAuthenticationFilter;

import com.fixflow.workspace.web.WorkspaceGuardFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@RequiredArgsConstructor
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * Everything else needs a bearer token from Prabhix Identity. There is no sign-in route here:
     * credentials, OTPs and sessions are Identity's, and the SPA arrives with a token already.
     */
    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/v1/mobistack/public/**",
            "/download/**",
            "/actuator/health/**",
            // Razorpay cannot present a token. Its authenticity is proved by the
            // HMAC signature over the raw body, checked inside the handler.
            "/api/v1/mobistack/billing/webhooks/razorpay",
            // Service-to-service. The JWT filter skips this prefix; PlatformAdminAuthFilter
            // accepts the shared token and names the acting staff member. A shop JWT is not
            // a credential here — requireAdmin refuses anything that is not the BFF.
            "/api/v1/mobistack/admin/**",
            "/internal/**"
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CatalogOnlyFilter catalogOnlyFilter;
    private final CatalogPlanFilter catalogPlanFilter;
    private final WorkspaceGuardFilter workspaceGuardFilter;
    private final ApiRateLimitFilter apiRateLimitFilter;
    private final PlatformAdminAuthFilter platformAdminAuthFilter;
    private final FixFlowProperties properties;
    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated())
                // Set here as well as at the reverse proxy: the API is also reached
                // directly in development and from the mobile app, and a header
                // that only exists in the Caddy config protects neither.
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .contentTypeOptions(withDefaults())
                        .referrerPolicy(referrer -> referrer.policy(
                                ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000))
                        .permissionsPolicyHeader(permissions -> permissions
                                .policy("camera=(self), geolocation=(), microphone=()")))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler()))
                .addFilterBefore(apiRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(platformAdminAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(workspaceGuardFilter, JwtAuthenticationFilter.class)
                .addFilterAfter(catalogOnlyFilter, WorkspaceGuardFilter.class)
                .addFilterAfter(catalogPlanFilter, CatalogOnlyFilter.class);

        return http.build();
    }

    /** Keep these in the security chain only — not as servlet filters before JWT. */
    @Bean
    public FilterRegistrationBean<WorkspaceGuardFilter> workspaceGuardRegistration(WorkspaceGuardFilter filter) {
        FilterRegistrationBean<WorkspaceGuardFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<PlatformAdminAuthFilter> platformAdminAuthRegistration(
            PlatformAdminAuthFilter filter) {
        FilterRegistrationBean<PlatformAdminAuthFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<CatalogPlanFilter> catalogPlanRegistration(CatalogPlanFilter filter) {
        FilterRegistrationBean<CatalogPlanFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<CatalogOnlyFilter> catalogOnlyRegistration(CatalogOnlyFilter filter) {
        FilterRegistrationBean<CatalogOnlyFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<ApiRateLimitFilter> apiRateLimitRegistration(ApiRateLimitFilter filter) {
        FilterRegistrationBean<ApiRateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // WEB_ORIGIN / PUBLIC_ORIGIN are the live SPA hosts (local compose uses :5176). Keep them
        // in sync with CORS so browser POSTs that send Origin are not rejected as "Invalid CORS
        // request" while GETs (no Origin, or cached preflight) still look healthy.
        java.util.LinkedHashSet<String> origins = new java.util.LinkedHashSet<>(
                properties.getCors().getAllowedOrigins());
        addOrigin(origins, properties.getAuth().getWebOrigin());
        addOrigin(origins, properties.getPlatform().getPublicOrigin());
        configuration.setAllowedOrigins(List.copyOf(origins));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        // Named rather than "*": credentials are allowed on this origin, so the
        // browser should not be able to attach arbitrary headers to those calls.
        configuration.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "Accept",
                "Accept-Language",
                "X-Requested-With",
                "Idempotency-Key",
                com.prabhix.identity.client.IdentityInternalClient.SERVICE_TOKEN_HEADER,
                com.prabhix.identity.client.IdentityInternalClient.ACTING_USER_HEADER,
                com.prabhix.identity.client.IdentityInternalClient.ACTING_REASON_HEADER,
                com.fixflow.workspace.web.WorkspaceGuardFilter.WORKSPACE_HEADER,
                com.fixflow.workspace.web.WorkspaceGuardFilter.WORKSPACE_HEADER_LEGACY,
                com.fixflow.common.web.ClientRequests.DEVICE_HEADER,
                com.fixflow.common.web.ClientRequests.DEVICE_HEADER_LEGACY,
                com.fixflow.common.web.CorrelationIdFilter.HEADER));
        configuration.setExposedHeaders(List.of("Content-Disposition",
                com.fixflow.common.web.CorrelationIdFilter.HEADER));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private static void addOrigin(java.util.Set<String> origins, String origin) {
        if (origin != null && !origin.isBlank()) {
            origins.add(origin.trim());
        }
    }

    private AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(ErrorCode.UNAUTHENTICATED.status().value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(),
                    ApiError.of(ErrorCode.UNAUTHENTICATED, "Authentication required.", request.getRequestURI()));
        };
    }

    private AccessDeniedHandler accessDeniedHandler() {
        return (request, response, deniedException) -> {
            response.setStatus(ErrorCode.FORBIDDEN.status().value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(),
                    ApiError.of(ErrorCode.FORBIDDEN, "You do not have permission to perform this action.",
                            request.getRequestURI()));
        };
    }
}
