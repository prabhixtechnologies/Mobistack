package com.fixflow.config;

import com.fixflow.security.CurrentUser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.Optional;
import java.util.UUID;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class JpaConfig {

    @Bean
    public AuditorAware<UUID> auditorAware() {
        // Empty for unauthenticated work such as Flyway callbacks and the demo seeder.
        return () -> CurrentUser.find().map(com.fixflow.security.UserPrincipal::getId);
    }
}
