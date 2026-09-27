package com.fixflow.security;

import com.prabhix.identity.client.ServiceTokenGuard;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformAdminAuthFilterTest {

    @Mock
    private ServiceTokenGuard serviceToken;
    @Mock
    private ServiceTokenAuthHandler authHandler;
    @Mock
    private FilterChain chain;

    @Test
    void usesSharedHandlerWhenServiceTokenConfigured() throws Exception {
        PlatformAdminAuthFilter filter = new PlatformAdminAuthFilter(serviceToken, authHandler);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/mobistack/admin/workspaces");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(serviceToken.configured()).thenReturn(true);
        when(authHandler.permitOrReject(request, response, serviceToken, "platform-admin")).thenReturn(false);

        filter.doFilterInternal(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        verify(authHandler).permitOrReject(request, response, serviceToken, "platform-admin");
    }

    @Test
    void bypassesAuthWhenServiceTokenNotConfigured() throws Exception {
        PlatformAdminAuthFilter filter = new PlatformAdminAuthFilter(serviceToken, authHandler);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/mobistack/admin/workspaces");
        when(serviceToken.configured()).thenReturn(false);

        filter.doFilterInternal(request, new MockHttpServletResponse(), chain);

        verify(authHandler, never()).permitOrReject(any(), any(), any(), any());
        verify(chain).doFilter(any(), any());
    }
}
