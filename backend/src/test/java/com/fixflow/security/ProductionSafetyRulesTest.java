package com.fixflow.security;

import com.fixflow.config.FixFlowProperties;
import com.prabhix.identity.client.IdentityClientProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionSafetyRulesTest {

    private static IdentityClientProperties identity(String issuer, String token) {
        return new IdentityClientProperties(issuer, null, null, null, null,
                "http://identity:8081", token, null);
    }

    @Test
    void rejectsDefaultFixflowDatabasePair() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("spring.datasource.username", "fixflow");
        env.setProperty("spring.datasource.password", "fixflow");
        assertThat(ProductionSafetyRules.validateDatabaseCredentials(env))
                .contains("default development database");
    }

    @Test
    void acceptsStrongDatabaseCredentials() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("spring.datasource.username", "mobistack");
        env.setProperty("spring.datasource.password", "xK9!mP2vLqR8nW4zT6yH1jF5dC0bA3e");
        assertThat(ProductionSafetyRules.validateDatabaseCredentials(env)).isNull();
    }

    @Test
    void rejectsWeakServiceToken() {
        assertThat(ProductionSafetyRules.validateIdentity(
                identity("https://id.example.com", "short-token")))
                .contains("weak");
    }

    @Test
    void rejectsSwaggerWhenEnabled() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("springdoc.api-docs.enabled", "true");
        assertThat(ProductionSafetyRules.validateSwagger(env)).contains("api-docs");
    }

    @Test
    void rejectsRazorpayWithoutWebhookSecret() {
        FixFlowProperties properties = new FixFlowProperties();
        properties.getRazorpay().setKeyId("rzp_live_x");
        properties.getRazorpay().setKeySecret("live_secret_value_1234567890");
        assertThat(ProductionSafetyRules.validateRazorpay(properties)).contains("webhook secret");
    }

    @Test
    void acceptsRazorpayWithStrongWebhookSecret() {
        FixFlowProperties properties = new FixFlowProperties();
        properties.getRazorpay().setKeyId("rzp_live_x");
        properties.getRazorpay().setKeySecret("live_secret_value_1234567890");
        properties.getRazorpay().setWebhookSecret("whsec_0123456789abcdef0123456789");
        assertThat(ProductionSafetyRules.validateRazorpay(properties)).isNull();
    }

    @Test
    void rejectsWeakRazorpayWebhookSecret() {
        FixFlowProperties properties = new FixFlowProperties();
        properties.getRazorpay().setKeyId("rzp_live_x");
        properties.getRazorpay().setKeySecret("live_secret_value_1234567890");
        properties.getRazorpay().setWebhookSecret("changeme");
        assertThat(ProductionSafetyRules.validateRazorpay(properties)).contains("weak");
    }

    @Test
    void rejectsDefaultFlywayCredentialsWhenSet() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("spring.datasource.username", "mobistack");
        env.setProperty("spring.datasource.password", "xK9!mP2vLqR8nW4zT6yH1jF5dC0bA3e");
        env.setProperty("spring.flyway.user", "mobistack");
        env.setProperty("spring.flyway.password", "mobistack");
        assertThat(ProductionSafetyRules.validateDatabaseCredentials(env)).contains("Flyway");
    }

    @Test
    void rejectsDevelopmentOtp() {
        FixFlowProperties properties = new FixFlowProperties();
        properties.getAuth().setDevOtp("424242");
        assertThat(ProductionSafetyRules.validateDevAuth(properties)).contains("development OTP");
    }

    @Test
    void rejectsDevelopmentSso() {
        FixFlowProperties properties = new FixFlowProperties();
        properties.getAuth().setDevSsoEnabled(true);
        assertThat(ProductionSafetyRules.validateDevAuth(properties)).contains("development SSO");
    }
}
