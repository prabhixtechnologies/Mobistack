package com.fixflow.security;

import com.fixflow.config.FixFlowProperties;
import com.prabhix.identity.client.IdentityClientProperties;
import com.prabhix.identity.client.ServiceTokenGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
@ExtendWith(MockitoExtension.class)
class ServiceTokenAuthHandlerTest {

    private static final String TOKEN = "01234567890123456789012345678901";

    @Mock
    private ObjectProvider<StringRedisTemplate> redis;

    private ServiceTokenAuthHandler handler;
    private ServiceTokenGuard guard;

    @BeforeEach
    void setUp() {
        handler = new ServiceTokenAuthHandler(new ObjectMapper(), new FixFlowProperties(), redis);
        guard = new ServiceTokenGuard(new IdentityClientProperties(
                "https://id.example.com", null, null, null, null, "http://identity:8081", TOKEN, null));
    }

    @Test
    void permitsMatchingToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/mobistack/admin/x");
        request.addHeader("X-Prabhix-Service-Token", TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(handler.permitOrReject(request, response, guard, "internal")).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void rejectsInvalidTokenWith401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/mobistack/admin/x");
        request.addHeader("X-Prabhix-Service-Token", "wrong");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(handler.permitOrReject(request, response, guard, "internal")).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void rateLimitsRepeatedFailures() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/mobistack/admin/workspaces");
        request.setRemoteAddr("203.0.113.10");
        request.addHeader("X-Prabhix-Service-Token", "wrong");
        MockHttpServletResponse response = new MockHttpServletResponse();

        for (int attempt = 0; attempt < ServiceTokenAuthHandler.FAILURE_LIMIT; attempt++) {
            handler.permitOrReject(request, response, guard, "platform-admin");
        }
        MockHttpServletResponse throttled = new MockHttpServletResponse();
        assertThat(handler.permitOrReject(request, throttled, guard, "platform-admin")).isFalse();
        assertThat(throttled.getStatus()).isEqualTo(429);
    }
}
