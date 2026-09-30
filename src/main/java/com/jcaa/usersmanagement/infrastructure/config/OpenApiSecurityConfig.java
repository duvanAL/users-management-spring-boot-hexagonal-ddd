package com.jcaa.usersmanagement.infrastructure.config;

import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT")
public class OpenApiSecurityConfig {

  @Bean
  OpenApiCustomizer optionalBearerForPublicRegistration() {
    return (OpenAPI openApi) -> {
      if (openApi.getPaths() == null || openApi.getPaths().get("/api/users") == null) {
        return;
      }

      final var createUserOperation = openApi.getPaths().get("/api/users").getPost();
      if (createUserOperation != null) {
        createUserOperation.setSecurity(
            List.of(new SecurityRequirement().addList("bearerAuth"), new SecurityRequirement()));
      }
    };
  }
}
