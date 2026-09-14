package com.fixflow.security;

import com.fixflow.config.FixFlowProperties;
import com.prabhix.identity.client.IdentityClientProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionSafetyGuardTest {

    private static IdentityClientProperties identity(String issuer, String serviceToken) {
        return new IdentityClientProperties(issuer, null, null, null, null,
                "http://identity:8081", serviceToken, null);
    }

    private static MockEnvironment profile(String name) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(name);
        return env;
    }

    @Test
    void prodRejectsMissingIssuer() {
        ProductionSafetyGuard guard = new ProductionSafetyGuard(profile("prod"), new FixFlowProperties(),
                identity("", "a-real-service-token"));
        assertThatThrownBy(guard::rejectUnsafeProduction)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IDENTITY_ISSUER");
    }

    @Test
    void prodRejectsLocalServiceToken() {
        ProductionSafetyGuard guard = new ProductionSafetyGuard(profile("prod"), new FixFlowProperties(),
                identity("https://id.prabhixtechnologies.com", "local-dev-identity-service-token"));
        assertThatThrownBy(guard::rejectUnsafeProduction)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("service token");
    }

    @Test
    void prodRejectsMissingServiceToken() {
        ProductionSafetyGuard guard = new ProductionSafetyGuard(profile("prod"), new FixFlowProperties(),
                identity("https://id.prabhixtechnologies.com", ""));
        assertThatThrownBy(guard::rejectUnsafeProduction)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IDENTITY_SERVICE_TOKEN");
    }

    @Test
    void prodRejectsDemoSeed() {
        FixFlowProperties properties = new FixFlowProperties();
        properties.getDemo().setSeedEnabled(true);
        ProductionSafetyGuard guard = new ProductionSafetyGuard(profile("prod"), properties,
                identity("https://id.prabhixtechnologies.com", "a-real-service-token"));
        assertThatThrownBy(guard::rejectUnsafeProduction)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("demo seed");
    }

    @Test
    void prodAcceptsCompleteConfiguration() {
        ProductionSafetyGuard guard = new ProductionSafetyGuard(profile("prod"), new FixFlowProperties(),
                identity("https://id.prabhixtechnologies.com", "a-real-service-token"));
        assertThatCode(guard::rejectUnsafeProduction).doesNotThrowAnyException();
    }

    @Test
    void devAllowsLocalConfiguration() {
        FixFlowProperties properties = new FixFlowProperties();
        properties.getDemo().setSeedEnabled(true);
        ProductionSafetyGuard guard = new ProductionSafetyGuard(profile("dev"), properties,
                identity("", "local-dev-identity-service-token"));
        assertThatCode(guard::rejectUnsafeProduction).doesNotThrowAnyException();
    }
}
