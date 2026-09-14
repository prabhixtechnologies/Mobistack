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

    /** The dev profile's service token; a prod process presenting it would be refused by Identity. */
    private static final String LOCAL_TOKEN_MARKER = "local-dev";

    private final Environment environment;
    private final FixFlowProperties properties;
    private final IdentityClientProperties identity;

    @PostConstruct
    void rejectUnsafeProduction() {
        if (!environment.acceptsProfiles(Profiles.of("prod"))) {
            return;
        }
        // No issuer means no bearer token is ever accepted: the process would pass its health check
        // while every signed-in person gets 401. Failing at boot is the honest version of that.
        if (!identity.enabled()) {
            throw new IllegalStateException("Production refused to start without an identity issuer (IDENTITY_ISSUER).");
        }
        String serviceToken = identity.serviceToken();
        if (serviceToken != null && serviceToken.toLowerCase().contains(LOCAL_TOKEN_MARKER)) {
            throw new IllegalStateException("Production refused a local/development identity service token.");
        }
        if (!identity.canCallInternal()) {
            throw new IllegalStateException(
                    "Production refused to start without the identity service token (IDENTITY_SERVICE_TOKEN): "
                            + "new sign-ups could not be mirrored and /internal/admin would be unreachable.");
        }
        if (properties.getDemo().isSeedEnabled()) {
            throw new IllegalStateException("Production refused to start with demo seed enabled.");
        }
    }
}
