package com.fixflow.admin.service;

import com.fixflow.common.error.ApiException;
import com.fixflow.common.error.ErrorCode;
import com.fixflow.shop.repository.ShopRepository;
import com.fixflow.workspace.repository.WorkspaceMembershipRepository;
import com.prabhix.identity.client.IdentityClientProperties;
import com.prabhix.identity.client.IdentityInternalClient;
import com.prabhix.identity.client.ServiceTokenGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Platform admin is the BFF's, not a shopkeeper's. A normal user JWT used to pass because
 * {@code users.system_admin} was the gate; that path is gone.
 */
@ExtendWith(MockitoExtension.class)
class PlatformAdminServiceTest {

    private static final String TOKEN = "bff-service-token";

    @Mock private ShopRepository shops;
    @Mock private WorkspaceMembershipRepository memberships;

    private PlatformAdminService service;
    private final UUID actor = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        IdentityClientProperties config = new IdentityClientProperties(
                "https://id.prabhix.test", null, null, null, null,
                "http://identity.test", TOKEN, null);
        service = new PlatformAdminService(shops, memberships, new ServiceTokenGuard(config));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void rejectsANormalUserJwt() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/mobistack/admin/workspaces");
        request.addHeader("Authorization", "Bearer shop-user-jwt");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThatThrownBy(() -> service.requireAdmin())
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void rejectsAMissingActingUserEvenWithTheServiceToken() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/mobistack/admin/workspaces");
        request.addHeader(IdentityInternalClient.SERVICE_TOKEN_HEADER, TOKEN);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThatThrownBy(() -> service.requireAdmin())
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.UNAUTHENTICATED);
    }

    @Test
    void acceptsTheServiceTokenAndActingUser() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/mobistack/admin/workspaces");
        request.addHeader(IdentityInternalClient.SERVICE_TOKEN_HEADER, TOKEN);
        request.addHeader(IdentityInternalClient.ACTING_USER_HEADER, actor.toString());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThat(service.requireAdmin()).isEqualTo(actor);
    }

    @Test
    void aWrongServiceTokenIsNotAShopAdminEither() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/mobistack/admin/workspaces/x/suspend");
        request.addHeader(IdentityInternalClient.SERVICE_TOKEN_HEADER, "not-the-token");
        request.addHeader(IdentityInternalClient.ACTING_USER_HEADER, actor.toString());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThatThrownBy(() -> service.requireAdmin())
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }
}
