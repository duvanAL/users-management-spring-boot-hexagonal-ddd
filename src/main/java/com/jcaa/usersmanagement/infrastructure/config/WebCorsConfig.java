package com.jcaa.usersmanagement.infrastructure.config;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class WebCorsConfig implements WebMvcConfigurer {

  private static final String[] ALLOWED_METHODS = {"GET", "POST", "PUT", "DELETE", "OPTIONS"};
  private static final String[] ALLOWED_HEADERS = {"Content-Type", "Authorization"};

  private final String[] allowedOrigins;

  public WebCorsConfig(@Value("${app.cors.allowed-origins:http://localhost:3000}") final String origins) {
    this.allowedOrigins = Arrays.stream(origins.split(","))
        .map(String::trim)
        .filter(origin -> !origin.isEmpty())
        .toArray(String[]::new);
  }

  @Override
  public void addCorsMappings(final CorsRegistry registry) {
    registry.addMapping("/**")
        .allowedOrigins(allowedOrigins)
        .allowedMethods(ALLOWED_METHODS)
        .allowedHeaders(ALLOWED_HEADERS)
        .allowCredentials(false)
        .maxAge(3600);
  }
}
