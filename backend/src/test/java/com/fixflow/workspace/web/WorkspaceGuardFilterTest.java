package com.fixflow.workspace.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixflow.security.UserPrincipal;
import com.fixflow.user.domain.User;
import com.fixflow.workspace.domain.WorkspaceMembership;
import com.fixflow.workspace.service.WorkspaceAccessService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkspaceGuardFilterTest {

    @Mock
    private WorkspaceAccessService workspaceAccessService;
    @Mock
    private FilterChain chain;

    private WorkspaceGuardFilter filter;
    private UUID userId;
    private UUID workspaceId;

    @BeforeEach
    void setUp() {
        filter = new WorkspaceGuardFilter(workspaceAccessService, new ObjectMapper().findAndRegisterModules());
        userId = UUID.randomUUID();
        workspaceId = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void businessApiWithoutASelectedWorkspaceIsRejected() throws Exception {
        authenticate(UserPrincipal.unscoped(user()));

        MockHttpServletResponse response = run("GET", "/api/v1/inventory", null);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("WORKSPACE_REQUIRED");
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void mismatchedWorkspaceHeaderIsRejectedNotTrusted() throws Exception {
        authenticate(new UserPrincipal(userId, workspaceId, "owner@prabhixtechnologies.com", "Abhishek", true,
                java.util.Set.of("OWNER"), java.util.Set.of()));

        MockHttpServletResponse response = run("GET", "/api/v1/inventory", UUID.randomUUID().toString());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("FORBIDDEN");
        verify(workspaceAccessService, never()).requireActive(any(), any());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void matchingHeaderReChecksMembershipThenContinues() throws Exception {
        authenticate(new UserPrincipal(userId, workspaceId, "owner@prabhixtechnologies.com", "Abhishek", true,
                java.util.Set.of("OWNER"), java.util.Set.of()));
        when(workspaceAccessService.requireActive(userId, workspaceId)).thenReturn(new WorkspaceMembership());

        MockHttpServletResponse response = run("GET", "/api/v1/inventory", workspaceId.toString());

        assertThat(response.getStatus()).isEqualTo(200);
        verify(workspaceAccessService).requireActive(userId, workspaceId);
        verify(chain).doFilter(any(), any());
    }

    @Test
    void workspaceDirectoryIsAllowedWithoutASelection() throws Exception {
        authenticate(UserPrincipal.unscoped(user()));

        MockHttpServletResponse response = run("GET", "/api/v1/workspaces", null);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(workspaceAccessService, never()).requireActive(any(), any());
        verify(chain).doFilter(any(), any());
    }

    @Test
    void paidJoinAndCancelAreAllowedWithoutASelectedWorkspace() throws Exception {
        authenticate(UserPrincipal.unscoped(user()));

        MockHttpServletResponse checkout = run("POST", "/api/v1/workspaces/join/checkout", null);
        MockHttpServletResponse complete = run("POST", "/api/v1/workspaces/join/complete", null);
        MockHttpServletResponse cancel = run("POST", "/api/v1/workspaces/" + workspaceId + "/join/cancel", null);

        assertThat(checkout.getStatus()).isEqualTo(200);
        assertThat(complete.getStatus()).isEqualTo(200);
        assertThat(cancel.getStatus()).isEqualTo(200);
        verify(workspaceAccessService, never()).requireActive(any(), any());
    }

    @Test
    void selectIsAllowedEvenWhenTheHeaderNamesTheTargetWorkspace() throws Exception {
        authenticate(UserPrincipal.unscoped(user()));

        MockHttpServletRequest request = new MockHttpServletRequest("POST",
                "/api/v1/workspaces/" + workspaceId + "/select");
        request.setServletPath("/api/v1/workspaces/" + workspaceId + "/select");
        request.addHeader(WorkspaceGuardFilter.WORKSPACE_HEADER, workspaceId.toString());
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(any(), any());
    }

    @Test
    void supportAndPresenceBypassTheGuard() throws Exception {
        authenticate(UserPrincipal.unscoped(user()));
        MockHttpServletResponse support = run("POST", "/api/v1/support/chat", null);
        MockHttpServletResponse presence = run("POST", "/api/v1/presence/heartbeat", null);

        assertThat(support.getStatus()).isEqualTo(200);
        assertThat(presence.getStatus()).isEqualTo(200);
        verify(workspaceAccessService, never()).requireActive(any(), any());
    }

    @Test
    void authEndpointsBypassTheGuard() throws Exception {
        authenticate(UserPrincipal.unscoped(user()));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        request.setServletPath("/api/v1/auth/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);

        verify(workspaceAccessService, never()).requireActive(any(), any());
        verify(chain).doFilter(any(), any());
    }

    private MockHttpServletResponse run(String method, String path, String header) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        if (header != null) {
            request.addHeader(WorkspaceGuardFilter.WORKSPACE_HEADER, header);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    private void authenticate(UserPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private User user() {
        User user = new User();
        user.setId(userId);
        user.setEmail("owner@prabhixtechnologies.com");
        user.setFullName("Abhishek Sharma");
        user.setActive(true);
        return user;
    }
}
