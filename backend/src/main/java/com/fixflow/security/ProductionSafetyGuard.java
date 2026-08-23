package com.fixflow.security;

import com.fixflow.config.FixFlowProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductionSafetyGuard {

    private static final String LOCAL_SECRET_MARKER = "do-not-use-in-production";

    private final Environment environment;
    private final FixFlowProperties properties;

    @PostConstruct
    void rejectUnsafeProduction() {
        if (!environment.acceptsProfiles(Profiles.of("prod"))) {
            return;
        }
        String secret = properties.getSecurity().getJwt().getSecret();
        if (secret != null && secret.toLowerCase().contains(LOCAL_SECRET_MARKER)) {
            throw new IllegalStateException("Production refused a local/development JWT secret.");
        }
        if (properties.getDemo().isSeedEnabled()) {
            throw new IllegalStateException("Production refused to start with demo seed enabled.");
        }
        if (properties.getAuth().isDevSsoEnabled()) {
            throw new IllegalStateException("Production refused to start with development SSO enabled.");
        }
    }
}
