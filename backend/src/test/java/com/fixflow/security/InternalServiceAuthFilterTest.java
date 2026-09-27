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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalServiceAuthFilterTest {

    @Mock
    private ServiceTokenGuard serviceToken;
    @Mock
    private ServiceTokenAuthHandler authHandler;
    @Mock
    private FilterChain chain;

    @Test
    void delegatesRejectionToSharedHandler() throws Exception {
        InternalServiceAuthFilter filter = new InternalServiceAuthFilter(serviceToken, authHandler);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/mobistack/admin/x");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(authHandler.permitOrReject(request, response, serviceToken, "internal")).thenReturn(false);

        filter.doFilterInternal(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        verify(authHandler).permitOrReject(request, response, serviceToken, "internal");
    }

    @Test
    void continuesWhenHandlerPermits() throws Exception {
        InternalServiceAuthFilter filter = new InternalServiceAuthFilter(serviceToken, authHandler);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/mobistack/admin/x");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(authHandler.permitOrReject(request, response, serviceToken, "internal")).thenReturn(true);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void skipsNonInternalPaths() throws Exception {
        InternalServiceAuthFilter filter = new InternalServiceAuthFilter(serviceToken, authHandler);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/mobistack/auth/me");
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        verify(authHandler, never()).permitOrReject(any(), any(), eq(serviceToken), any());
        verify(chain).doFilter(any(), any());
    }
}
