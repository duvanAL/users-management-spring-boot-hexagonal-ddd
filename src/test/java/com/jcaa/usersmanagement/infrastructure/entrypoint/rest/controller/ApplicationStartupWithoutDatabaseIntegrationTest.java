package com.jcaa.usersmanagement.infrastructure.entrypoint.rest.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

/** Verifies that Render's liveness endpoint remains available during a database cold start. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.main.web-application-type=servlet",
      "spring.sql.init.mode=never",
      "db.host=127.0.0.1",
      "db.port=1",
      "db.pool.maximum-size=5",
      "db.pool.minimum-idle=0",
      "db.pool.connection-timeout-ms=1000",
      "app.seed.admin.enabled=false"
    })
class ApplicationStartupWithoutDatabaseIntegrationTest {

  @Autowired private TestRestTemplate restTemplate;

  @Test
  void shouldStartHttpServerAndReportLivenessWhilePostgresIsUnavailable() {
    final ResponseEntity<String> response = restTemplate.getForEntity("/health", String.class);

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(response.getBody()).contains("\"status\":\"UP\"");
  }
}
