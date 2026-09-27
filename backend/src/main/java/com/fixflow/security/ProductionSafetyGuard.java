package com.fixflow.security;

import com.fixflow.config.FixFlowProperties;
import com.prabhix.identity.client.IdentityClientProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Refuses to start a production process whose configuration would let it run in a state that
 * looks healthy and is not.
 */
@Component
@RequiredArgsConstructor
public class ProductionSafetyGuard {

    private final Environment environment;
    private final FixFlowProperties properties;
    private final IdentityClientProperties identity;

    /**
     * Runs only under the {@code prod} profile. {@code dev}, {@code test}, {@code local} and other
     * non-production profiles skip every check so compose and CI can use default credentials.
     */
    @PostConstruct
    void rejectUnsafeProduction() {
        if (!environment.acceptsProfiles(Profiles.of("prod"))) {
            return;
        }
        failIfPresent(ProductionSafetyRules.validateIdentity(identity));
        failIfPresent(ProductionSafetyRules.validateDatabaseCredentials(environment));
        failIfPresent(ProductionSafetyRules.validateHttps(properties));
        failIfPresent(ProductionSafetyRules.validateSwagger(environment));
        failIfPresent(ProductionSafetyRules.validateDevAuth(properties));
        failIfPresent(ProductionSafetyRules.validateRazorpay(properties));
    }

    private static void failIfPresent(String message) {
        if (message != null) {
            throw new IllegalStateException(message);
        }
    }
}
