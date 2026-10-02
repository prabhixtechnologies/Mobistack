package com.fixflow.security;

import com.fixflow.billing.service.BillingService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogOnlyFilterTest {

    @Mock
    private BillingService billingService;
    @Mock
    private FilterChain chain;

    private CatalogOnlyFilter filter;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        filter = new CatalogOnlyFilter(billingService, new ObjectMapper());
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void fullShopPlansReachInventory() throws Exception {
        UUID shopId = UUID.randomUUID();
        signIn(shopId);
        when(billingService.catalogOnly(shopId)).thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/mobistack/inventory");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void catalogOnlyPlansBlockInventoryButAllowCommons() throws Exception {
        UUID shopId = UUID.randomUUID();
        signIn(shopId);
        when(billingService.catalogOnly(shopId)).thenReturn(true);

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilterInternal(new MockHttpServletRequest("POST", "/api/v1/mobistack/sales"), blocked, chain);
        assertThat(blocked.getStatus()).isEqualTo(402);

        MockHttpServletResponse commons = new MockHttpServletResponse();
        filter.doFilterInternal(new MockHttpServletRequest("GET", "/api/v1/mobistack/commons/brands"), commons, chain);
        verify(chain).doFilter(any(), any());

        MockHttpServletRequest searchRequest =
                new MockHttpServletRequest("GET", "/api/v1/mobistack/search");
        MockHttpServletResponse search = new MockHttpServletResponse();
        filter.doFilterInternal(searchRequest, search, chain);
        verify(chain).doFilter(searchRequest, search);
    }

    @Test
    void systemAdminsAreHeldToTheShopsCatalogOnlyPlan() throws Exception {
        UUID shopId = UUID.randomUUID();
        signIn(shopId);
        when(billingService.catalogOnly(shopId)).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/mobistack/inventory");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        verify(billingService, never()).callerIsSystemAdmin();
        assertThat(response.getStatus()).isEqualTo(402);
    }

    @Test
    void anonymousRequestsPassThrough() throws Exception {
        filter.doFilterInternal(new MockHttpServletRequest("GET", "/api/v1/mobistack/public/brand"), new MockHttpServletResponse(), chain);
        verify(chain).doFilter(any(), any());
        verify(billingService, never()).catalogOnly(any());
    }

    private static void signIn(UUID shopId) {
        UserPrincipal principal = new UserPrincipal(
                UUID.randomUUID(), shopId, "a@b.c", "A", true, Set.of(), Set.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
