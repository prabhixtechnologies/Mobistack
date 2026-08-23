package com.fixflow.security;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@RequiredArgsConstructor
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/register-shop",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password",
            "/api/v1/auth/request-otp",
            "/api/v1/auth/verify-otp",
            "/api/v1/auth/methods",
            "/api/v1/auth/register",
            "/api/v1/auth/magic-link",
            "/api/v1/auth/magic-link/consume",
            "/api/v1/auth/email-otp",
            "/api/v1/auth/email-otp/verify",
            "/api/v1/auth/phone/start",
            "/api/v1/auth/phone/verify",
            "/api/v1/auth/whatsapp/start",
            "/api/v1/auth/whatsapp/verify",
            "/api/v1/auth/sso/dev",
            "/api/v1/auth/sso/google/start",
            "/api/v1/auth/sso/google",
            "/api/v1/public/**",
            "/api/v1/billing/webhooks/**",
            "/actuator/health/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final WorkspaceGuardFilter workspaceGuardFilter;
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
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler()))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(workspaceGuardFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    /** Keep the guard in the security chain only — not as a servlet filter before JWT. */
    @Bean
    public FilterRegistrationBean<WorkspaceGuardFilter> workspaceGuardRegistration(WorkspaceGuardFilter filter) {
        FilterRegistrationBean<WorkspaceGuardFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.getCors().getAllowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Content-Disposition"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
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
