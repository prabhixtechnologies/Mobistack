package com.fixflow.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    @ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true", matchIfMissing = true)
    public OpenAPI mobiStackOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("MobiStack API")
                        .version("v1")
                        .description("""
                                Mobile repair shop management platform.

                                All business endpoints require a Prabhix Identity bearer token. Sign-in happens on
                                Identity; this API only resolves shop authority from the selected workspace.
                                """)
                        .contact(new Contact().name("MobiStack").email("support@prabhixtechnologies.com"))
                        .license(new License().name("Proprietary")))
                .servers(List.of(new Server().url("/").description("Current host")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Prabhix Identity access token")));
    }
}
