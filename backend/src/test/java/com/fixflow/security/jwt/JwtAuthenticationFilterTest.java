package com.fixflow.security.jwt;

import tools.jackson.databind.ObjectMapper;
import com.fixflow.common.error.ErrorCode;
import com.fixflow.user.repository.UserRepository;
import com.fixflow.workspace.service.WorkspaceAccessService;
import com.prabhix.identity.client.IdentityTokenException;
import com.prabhix.identity.client.IdentityTokenVerifier;
import com.prabhix.identity.client.IdentityUserMirror;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Shop JWTs must not become a principal on platform admin — that path is the BFF's, over the
 * service token.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock private IdentityTokenVerifier tokenVerifier;
    @Mock private UserRepository userRepository;
    @Mock private WorkspaceAccessService workspaceAccessService;
    @Mock private IdentityUserMirror identityUserMirror;
    @Mock private FilterChain chain;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(
                tokenVerifier, userRepository, workspaceAccessService, identityUserMirror,
                new ObjectMapper());
    }

    @Test
    void adminRoutesDoNotConsumeAShopBearerToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/workspaces");
        request.addHeader("Authorization", "Bearer shop-user-jwt");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(request, response);
        verify(tokenVerifier, never()).verify(any());
    }

    @Test
    void internalRoutesDoNotConsumeAShopBearerToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/organizations");
        request.addHeader("Authorization", "Bearer shop-user-jwt");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(tokenVerifier, never()).verify(any());
    }

    @Test
    void anonymousHealthStillRunsWhenTheBrowserSendsAnExpiredAccessToken() throws Exception {
        when(tokenVerifier.verify("expired"))
                .thenThrow(new IdentityTokenException(IdentityTokenException.Reason.EXPIRED, "expired"));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        request.addHeader("Authorization", "Bearer expired");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(request, response);
    }

    @Test
    void protectedRoutesRejectAnExpiredToken() throws Exception {
        when(tokenVerifier.verify("expired"))
                .thenThrow(new IdentityTokenException(IdentityTokenException.Reason.EXPIRED, "expired"));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        request.addHeader("Authorization", "Bearer expired");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(ErrorCode.TOKEN_EXPIRED.status().value());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void treatsHealthAsAnonymous() {
        MockHttpServletRequest health = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletRequest me = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        assertThat(JwtAuthenticationFilter.isAnonymousOk(health)).isTrue();
        assertThat(JwtAuthenticationFilter.isAnonymousOk(me)).isFalse();
    }
}
