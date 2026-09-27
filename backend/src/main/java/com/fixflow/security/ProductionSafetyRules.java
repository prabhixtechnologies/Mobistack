package com.fixflow.security;

import com.fixflow.config.FixFlowProperties;
import com.prabhix.identity.client.IdentityClientProperties;
import org.springframework.core.env.Environment;

import java.util.Locale;
import java.util.Set;

/**
 * Pure checks used by {@link ProductionSafetyGuard} at startup. Kept separate so each rule is
 * testable without Spring context.
 */
final class ProductionSafetyRules {

    private static final String LOCAL_TOKEN_MARKER = "local-dev";
    private static final int MIN_SERVICE_TOKEN_LENGTH = 32;
    private static final int MIN_WEBHOOK_SECRET_LENGTH = 16;

    private static final Set<String> WEAK_SECRETS = Set.of(
            "changeme",
            "password",
            "secret",
            "test",
            "dev",
            "admin",
            "12345678",
            "local-dev-identity-service-token");

    private ProductionSafetyRules() {
    }

    static String validateIdentity(IdentityClientProperties identity) {
        if (!identity.enabled()) {
            return "Production refused to start without an identity issuer (IDENTITY_ISSUER).";
        }
        String token = identity.serviceToken();
        if (token == null || token.isBlank()) {
            return "Production refused to start without the identity service token (IDENTITY_SERVICE_TOKEN): "
                    + "new sign-ups could not be mirrored and /internal/admin would be unreachable.";
        }
        if (token.toLowerCase(Locale.ROOT).contains(LOCAL_TOKEN_MARKER)) {
            return "Production refused a local/development identity service token.";
        }
        if (token.length() < MIN_SERVICE_TOKEN_LENGTH || isWeakSecret(token)) {
            return "Production refused a weak or too-short identity service token.";
        }
        return null;
    }

    static String validateDatabaseCredentials(Environment environment) {
        String username = environment.getProperty("spring.datasource.username", "");
        String password = environment.getProperty("spring.datasource.password", "");
        if (password == null || password.isBlank()) {
            return "Production refused to start with a blank database password.";
        }
        if (isDefaultDatabasePair(username, password)) {
            return "Production refused default development database credentials.";
        }
        String flywayPassword = environment.getProperty("spring.flyway.password");
        if (flywayPassword != null && !flywayPassword.isBlank()) {
            String flywayUser = environment.getProperty("spring.flyway.user", username);
            if (isDefaultDatabasePair(flywayUser, flywayPassword)) {
                return "Production refused default development Flyway credentials.";
            }
        }
        return null;
    }

    static String validateHttps(FixFlowProperties properties) {
        if (!properties.getPlatform().isRequireHttps()) {
            return "Production refused to start without fixflow.platform.require-https=true.";
        }
        return null;
    }

    static String validateSwagger(Environment environment) {
        if (isEnabled(environment, "springdoc.api-docs.enabled")) {
            return "Production refused to start with springdoc.api-docs.enabled=true.";
        }
        if (isEnabled(environment, "springdoc.swagger-ui.enabled")) {
            return "Production refused to start with springdoc.swagger-ui.enabled=true.";
        }
        return null;
    }

    static String validateDevAuth(FixFlowProperties properties) {
        if (properties.getDemo().isSeedEnabled()) {
            return "Production refused to start with demo seed enabled.";
        }
        if (properties.getAuth().isDevSsoEnabled()) {
            return "Production refused to start with development SSO enabled.";
        }
        String devOtp = properties.getAuth().getDevOtp();
        if (devOtp != null && !devOtp.isBlank()) {
            return "Production refused to start with a development OTP configured.";
        }
        return null;
    }

    static String validateRazorpay(FixFlowProperties properties) {
        FixFlowProperties.Razorpay razorpay = properties.getRazorpay();
        if (!razorpay.configured()) {
            return null;
        }
        String webhook = razorpay.getWebhookSecret();
        if (webhook == null || webhook.isBlank()) {
            return "Production refused to start with Razorpay keys configured but no webhook secret.";
        }
        if (webhook.length() < MIN_WEBHOOK_SECRET_LENGTH || isWeakSecret(webhook)) {
            return "Production refused a weak or too-short Razorpay webhook secret.";
        }
        return null;
    }

    static boolean isDefaultDatabasePair(String username, String password) {
        String user = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        String pass = password.trim().toLowerCase(Locale.ROOT);
        if ("fixflow".equals(user) && "fixflow".equals(pass)) {
            return true;
        }
        if ("mobistack".equals(user) && "mobistack".equals(pass)) {
            return true;
        }
        if ("postgres".equals(user) && "postgres".equals(pass)) {
            return true;
        }
        return isWeakSecret(pass);
    }

    static boolean isWeakSecret(String value) {
        if (value == null) {
            return true;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return WEAK_SECRETS.contains(normalized);
    }

    private static boolean isEnabled(Environment environment, String key) {
        return Boolean.TRUE.equals(environment.getProperty(key, Boolean.class, false));
    }
}
