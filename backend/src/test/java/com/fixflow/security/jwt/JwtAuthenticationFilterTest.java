package com.fixflow.security.jwt;

import tools.jackson.databind.ObjectMapper;
import com.fixflow.auth.service.DeviceSessionService;
import com.fixflow.common.error.ErrorCode;
import com.fixflow.config.FixFlowProperties;
import com.fixflow.security.Permission;
import com.fixflow.security.UserPrincipal;
import com.fixflow.user.repository.UserRepository;
import com.fixflow.workspace.service.WorkspaceAccessService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private DeviceSessionService deviceSessionService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private WorkspaceAccessService workspaceAccessService;
    @Mock
    private FilterChain chain;

    private JwtService jwtService;
    private JwtAuthenticationFilter filter;
    private UserPrincipal principal;

    @BeforeEach
    void setUp() {
        jwtService = jwtService(Duration.ofMinutes(5));
        filter = newFilter(jwtService);
        principal = new UserPrincipal(UUID.randomUUID(), UUID.randomUUID(), "admin@prabhixtechnologies.com",
                "Admin", true, Set.of("OWNER"), Set.of(Permission.SETTINGS_READ));
    }

    @Test
    void loginStillRunsWhenTheBrowserSendsAnExpiredAccessToken() throws Exception {
        jwtService = jwtService(Duration.ofMillis(1));
        filter = newFilter(jwtService);
        String token = jwtService.createAccessToken(principal, "tablet-1");
        Thread.sleep(20);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(request, response);
        verify(deviceSessionService, never()).isLive(any(), any());
    }

    @Test
    void loginStillRunsWhenTheStoredSessionWasReplaced() throws Exception {
        String token = jwtService.createAccessToken(principal, "old-screen");
        when(deviceSessionService.isLive(principal.getId(), "old-screen")).thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(request, response);
    }

    @Test
    void protectedRoutesStillRejectAReplacedSession() throws Exception {
        String token = jwtService.createAccessToken(principal, "old-screen");
        when(deviceSessionService.isLive(principal.getId(), "old-screen")).thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(ErrorCode.SESSION_REPLACED.status().value());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void treatsSignInPathsAsAnonymous() {
        MockHttpServletRequest login = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        MockHttpServletRequest me = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        assertThat(JwtAuthenticationFilter.isAnonymousOk(login)).isTrue();
        assertThat(JwtAuthenticationFilter.isAnonymousOk(me)).isFalse();
    }

    private JwtAuthenticationFilter newFilter(JwtService service) {
        return new JwtAuthenticationFilter(
                service, deviceSessionService, userRepository, workspaceAccessService, objectMapper());
    }

    private static JwtService jwtService(Duration ttl) {
        FixFlowProperties properties = new FixFlowProperties();
        properties.getSecurity().getJwt()
                .setSecret("fixflow-test-signing-key-that-is-definitely-longer-than-sixty-four-chars");
        properties.getSecurity().getJwt().setAccessTokenTtl(ttl);
        IdentityKeySource identityKeys = new IdentityKeySource(properties, RestClient.builder());
        JwtService service = new JwtService(properties, identityKeys);
        service.init();
        return service;
    }

    private static ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
