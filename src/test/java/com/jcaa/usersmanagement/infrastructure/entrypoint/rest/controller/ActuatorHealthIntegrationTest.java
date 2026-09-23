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
    registry.add("db.host", POSTGRES::getHost);
    registry.add("db.port", POSTGRES::getFirstMappedPort);
    registry.add("db.name", POSTGRES::getDatabaseName);
    registry.add("db.username", POSTGRES::getUsername);
    registry.add("db.password", POSTGRES::getPassword);
    registry.add("db.sslmode", () -> "disable");
    registry.add("db.pool.maximum-size", () -> "5");
    registry.add("db.pool.minimum-idle", () -> "1");
    registry.add("db.pool.connection-timeout-ms", () -> "30000");
    registry.add("app.email.enabled", () -> "false");
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
