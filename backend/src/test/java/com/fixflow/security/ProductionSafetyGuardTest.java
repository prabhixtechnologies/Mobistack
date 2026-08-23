package com.fixflow.security;

import com.fixflow.config.FixFlowProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionSafetyGuardTest {

    @Test
    void prodRejectsKnownLocalJwt() {
        FixFlowProperties properties = new FixFlowProperties();
        properties.getSecurity().getJwt().setSecret("fixflow-local-development-signing-key-do-not-use-in-production-0123456789");
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        ProductionSafetyGuard guard = new ProductionSafetyGuard(env, properties);
        assertThatThrownBy(guard::rejectUnsafeProduction)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT");
    }

    @Test
    void prodRejectsDemoSeed() {
        FixFlowProperties properties = new FixFlowProperties();
        properties.getSecurity().getJwt().setSecret("a-production-grade-signing-key-that-is-longer-than-sixty-four-characters");
        properties.getDemo().setSeedEnabled(true);
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        ProductionSafetyGuard guard = new ProductionSafetyGuard(env, properties);
        assertThatThrownBy(guard::rejectUnsafeProduction).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void devAllowsLocalSecret() {
        FixFlowProperties properties = new FixFlowProperties();
        properties.getSecurity().getJwt().setSecret("fixflow-local-development-signing-key-do-not-use-in-production-0123456789");
        properties.getDemo().setSeedEnabled(true);
        properties.getAuth().setDevSsoEnabled(true);
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");
        ProductionSafetyGuard guard = new ProductionSafetyGuard(env, properties);
        assertThatCode(guard::rejectUnsafeProduction).doesNotThrowAnyException();
    }
}
