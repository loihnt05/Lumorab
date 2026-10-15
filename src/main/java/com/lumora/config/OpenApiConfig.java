package com.lumora.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
  @Bean
  OpenAPI lumoraOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Lumora API")
                .version("v1")
                .description(
                    "Foundation API. Business identity and authorization follow in Stage 1."));
  }
}
