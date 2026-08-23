package com.fixflow.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI fixFlowOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("FixFlow API")
                        .version("v1")
                        .description("""
                                Mobile repair shop management platform.

                                All endpoints except `/api/v1/auth/login`, `/api/v1/auth/refresh` and
                                `/api/v1/auth/register-shop` require a Bearer access token. Every request is
                                scoped to the shop encoded in that token.
                                """)
                        .contact(new Contact().name("FixFlow").email("support@fixflow.app"))
                        .license(new License().name("Proprietary")))
                .servers(List.of(new Server().url("/").description("Current host")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Access token returned by /api/v1/auth/login")));
    }
}
