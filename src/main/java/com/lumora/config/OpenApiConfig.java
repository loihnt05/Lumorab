package com.lumora.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
  @Bean
  OpenAPI lumoraOpenApi() {
    return new OpenAPI()
        .components(
            new Components()
                .addSecuritySchemes(
                    "clerkBearer",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description(
                            "Clerk session token. Roles and property permissions come from PostgreSQL.")))
        .info(
            new Info()
                .title("Lumora API")
                .version("v1")
                .description("Lumora identity, property authorization and room inventory API."));
  }
}
