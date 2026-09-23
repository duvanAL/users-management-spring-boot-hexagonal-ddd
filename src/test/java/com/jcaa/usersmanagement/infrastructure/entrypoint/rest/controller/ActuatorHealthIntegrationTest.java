package com.jcaa.usersmanagement.infrastructure.entrypoint.rest.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.main.web-application-type=servlet",
        "management.endpoints.web.exposure.include=health",
        "management.endpoint.health.show-components=always",
        "management.endpoint.health.show-details=never"
    })
class ActuatorHealthIntegrationTest {

  @Container
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:17-alpine");

  @LocalServerPort private int port;
  @Autowired private TestRestTemplate restTemplate;
  @Autowired private ObjectMapper objectMapper;

  @DynamicPropertySource
  static void databaseProperties(final DynamicPropertyRegistry registry) {
    registry.add("DB_HOST", POSTGRES::getHost);
    registry.add("DB_PORT", POSTGRES::getFirstMappedPort);
    registry.add("DB_NAME", POSTGRES::getDatabaseName);
    registry.add("DB_USERNAME", POSTGRES::getUsername);
    registry.add("DB_PASSWORD", POSTGRES::getPassword);
    registry.add("DB_SSLMODE", () -> "disable");
    registry.add("db.pool.maximum-size", () -> "5");
    registry.add("db.pool.minimum-idle", () -> "1");
    registry.add("db.pool.connection-timeout-ms", () -> "30000");
    registry.add("APP_EMAIL_ENABLED", () -> "false");
  }

  @Test
  void shouldReportPostgresComponentAsUpWithoutExposingDetails() throws Exception {
    // Arrange & Act
    final ResponseEntity<String> response =
        restTemplate.getForEntity("http://localhost:" + port + "/actuator/health", String.class);

    // Assert
    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    final JsonNode body = objectMapper.readTree(response.getBody());
    assertThat(body.path("status").asText()).isEqualTo("UP");
    assertThat(body.path("components").path("db").path("status").asText())
        .as(response.getBody())
        .isEqualTo("UP");
    assertThat(body.path("components").path("db").has("details")).isFalse();
  }

  @Test
  void shouldKeepLegacyHealthEndpointAvailable() throws Exception {
    // Arrange & Act
    final ResponseEntity<String> response =
        restTemplate.getForEntity("http://localhost:" + port + "/health", String.class);

    // Assert
    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(objectMapper.readTree(response.getBody()).path("status").asText()).isEqualTo("UP");
  }
}
