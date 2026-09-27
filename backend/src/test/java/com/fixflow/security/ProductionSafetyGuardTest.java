package com.fixflow.security;

import com.fixflow.config.FixFlowProperties;
import com.prabhix.identity.client.IdentityClientProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionSafetyGuardTest {

    private static final String STRONG_TOKEN = "01234567890123456789012345678901";
    private static final String STRONG_DB_PASSWORD = "xK9!mP2vLqR8nW4zT6yH1jF5dC0bA3e";

    private static IdentityClientProperties identity(String issuer, String serviceToken) {
        return new IdentityClientProperties(issuer, null, null, null, null,
                "http://identity:8081", serviceToken, null);
    }

    private static MockEnvironment profile(String name) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(name);
        return env;
    }

    private static MockEnvironment productionDatabase() {
        MockEnvironment env = profile("prod");
        env.setProperty("spring.datasource.username", "mobistack");
        env.setProperty("spring.datasource.password", STRONG_DB_PASSWORD);
        env.setProperty("springdoc.api-docs.enabled", "false");
        env.setProperty("springdoc.swagger-ui.enabled", "false");
        return env;
    }

    private static FixFlowProperties productionFixflow() {
        FixFlowProperties properties = new FixFlowProperties();
        properties.getPlatform().setRequireHttps(true);
        return properties;
    }

    @Test
    void prodRejectsMissingIssuer() {
        ProductionSafetyGuard guard = new ProductionSafetyGuard(productionDatabase(), productionFixflow(),
                identity("", STRONG_TOKEN));
        assertThatThrownBy(guard::rejectUnsafeProduction)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IDENTITY_ISSUER");
    }

    @Test
    void prodRejectsLocalServiceToken() {
        ProductionSafetyGuard guard = new ProductionSafetyGuard(productionDatabase(), productionFixflow(),
                identity("https://id.prabhixtechnologies.com", "local-dev-identity-service-token"));
        assertThatThrownBy(guard::rejectUnsafeProduction)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("service token");
    }

    @Test
    void prodRejectsMissingServiceToken() {
        ProductionSafetyGuard guard = new ProductionSafetyGuard(productionDatabase(), productionFixflow(),
                identity("https://id.prabhixtechnologies.com", ""));
        assertThatThrownBy(guard::rejectUnsafeProduction)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IDENTITY_SERVICE_TOKEN");
    }

    @Test
    void prodRejectsDefaultDatabaseCredentials() {
        MockEnvironment env = profile("prod");
        env.setProperty("spring.datasource.username", "fixflow");
        env.setProperty("spring.datasource.password", "fixflow");
        env.setProperty("springdoc.api-docs.enabled", "false");
        env.setProperty("springdoc.swagger-ui.enabled", "false");
        ProductionSafetyGuard guard = new ProductionSafetyGuard(env, productionFixflow(),
                identity("https://id.prabhixtechnologies.com", STRONG_TOKEN));
        assertThatThrownBy(guard::rejectUnsafeProduction)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("database");
    }

    @Test
    void prodRejectsMissingHttpsRequirement() {
        FixFlowProperties properties = new FixFlowProperties();
        properties.getPlatform().setRequireHttps(false);
        ProductionSafetyGuard guard = new ProductionSafetyGuard(productionDatabase(), properties,
                identity("https://id.prabhixtechnologies.com", STRONG_TOKEN));
        assertThatThrownBy(guard::rejectUnsafeProduction)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("require-https");
    }

    @Test
    void prodRejectsEnabledSwagger() {
        MockEnvironment env = productionDatabase();
        env.setProperty("springdoc.swagger-ui.enabled", "true");
        ProductionSafetyGuard guard = new ProductionSafetyGuard(env, productionFixflow(),
                identity("https://id.prabhixtechnologies.com", STRONG_TOKEN));
        assertThatThrownBy(guard::rejectUnsafeProduction)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("swagger-ui");
    }

    @Test
    void prodRejectsRazorpayWithoutWebhookSecret() {
        FixFlowProperties properties = productionFixflow();
        properties.getRazorpay().setKeyId("rzp_live_x");
        properties.getRazorpay().setKeySecret("01234567890123456789012345678901");
        ProductionSafetyGuard guard = new ProductionSafetyGuard(productionDatabase(), properties,
                identity("https://id.prabhixtechnologies.com", STRONG_TOKEN));
        assertThatThrownBy(guard::rejectUnsafeProduction)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("webhook");
    }

    @Test
    void prodRejectsDemoSeed() {
        FixFlowProperties properties = productionFixflow();
        properties.getDemo().setSeedEnabled(true);
        ProductionSafetyGuard guard = new ProductionSafetyGuard(productionDatabase(), properties,
                identity("https://id.prabhixtechnologies.com", STRONG_TOKEN));
        assertThatThrownBy(guard::rejectUnsafeProduction)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("demo seed");
    }

    @Test
    void prodAcceptsCompleteConfiguration() {
        ProductionSafetyGuard guard = new ProductionSafetyGuard(productionDatabase(), productionFixflow(),
                identity("https://id.prabhixtechnologies.com", STRONG_TOKEN));
        assertThatCode(guard::rejectUnsafeProduction).doesNotThrowAnyException();
    }

    @Test
    void devAllowsLocalConfiguration() {
        FixFlowProperties properties = new FixFlowProperties();
        properties.getDemo().setSeedEnabled(true);
        properties.getPlatform().setRequireHttps(false);
        MockEnvironment env = profile("dev");
        env.setProperty("spring.datasource.username", "fixflow");
        env.setProperty("spring.datasource.password", "fixflow");
        ProductionSafetyGuard guard = new ProductionSafetyGuard(env, properties,
                identity("", "local-dev-identity-service-token"));
        assertThatCode(guard::rejectUnsafeProduction).doesNotThrowAnyException();
    }

    @Test
    void testProfileSkipsValidation() {
        FixFlowProperties properties = new FixFlowProperties();
        properties.getDemo().setSeedEnabled(true);
        MockEnvironment env = profile("test");
        env.setProperty("spring.datasource.username", "mobistack");
        env.setProperty("spring.datasource.password", "mobistack");
        env.setProperty("springdoc.swagger-ui.enabled", "true");
        ProductionSafetyGuard guard = new ProductionSafetyGuard(env, properties,
                identity("", ""));
        assertThatCode(guard::rejectUnsafeProduction).doesNotThrowAnyException();
    }

    @Test
    void localProfileSkipsValidation() {
        ProductionSafetyGuard guard = new ProductionSafetyGuard(profile("local"), productionFixflow(),
                identity("", "local-dev-identity-service-token"));
        assertThatCode(guard::rejectUnsafeProduction).doesNotThrowAnyException();
    }

    @Test
    void prodRejectsWeakServiceToken() {
        ProductionSafetyGuard guard = new ProductionSafetyGuard(productionDatabase(), productionFixflow(),
                identity("https://id.prabhixtechnologies.com", "changeme"));
        assertThatThrownBy(guard::rejectUnsafeProduction)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("weak");
    }

    @Test
    void prodRejectsEnabledApiDocs() {
        MockEnvironment env = productionDatabase();
        env.setProperty("springdoc.api-docs.enabled", "true");
        ProductionSafetyGuard guard = new ProductionSafetyGuard(env, productionFixflow(),
                identity("https://id.prabhixtechnologies.com", STRONG_TOKEN));
        assertThatThrownBy(guard::rejectUnsafeProduction)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("api-docs");
    }

    @Test
    void prodRejectsDevelopmentOtp() {
        FixFlowProperties properties = productionFixflow();
        properties.getAuth().setDevOtp("999999");
        ProductionSafetyGuard guard = new ProductionSafetyGuard(productionDatabase(), properties,
                identity("https://id.prabhixtechnologies.com", STRONG_TOKEN));
        assertThatThrownBy(guard::rejectUnsafeProduction)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("development OTP");
    }

    @Test
    void prodRejectsWeakRazorpayWebhookSecret() {
        FixFlowProperties properties = productionFixflow();
        properties.getRazorpay().setKeyId("rzp_live_x");
        properties.getRazorpay().setKeySecret("01234567890123456789012345678901");
        properties.getRazorpay().setWebhookSecret("secret");
        ProductionSafetyGuard guard = new ProductionSafetyGuard(productionDatabase(), properties,
                identity("https://id.prabhixtechnologies.com", STRONG_TOKEN));
        assertThatThrownBy(guard::rejectUnsafeProduction)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("webhook");
    }
}
