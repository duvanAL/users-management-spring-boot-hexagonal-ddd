package com.jcaa.usersmanagement.infrastructure.adapter.persistence.config;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jcaa.usersmanagement.infrastructure.config.ConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class DatabaseConfigTest {
  private static final String HOST = "localhost";
  private static final String DATABASE = "crud_usuarios";
  private static final String USERNAME = "test_user";
  private static final String PASSWORD = "test_secret";
  private static final String SSL_MODE = "require";
  private static final int PORT = 5432;

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t"})
  void shouldRejectMissingConnectionValues(final String value) {
    // Arrange
    final String expectedPasswordError = "Invalid or missing configuration property: DB_PASSWORD.";

    // Act / Assert
    assertAll(
        () -> assertThrows(ConfigurationException.class,
            () -> new DatabaseConfig(value, PORT, DATABASE, USERNAME, PASSWORD, SSL_MODE)),
        () -> assertThrows(ConfigurationException.class,
            () -> new DatabaseConfig(HOST, PORT, value, USERNAME, PASSWORD, SSL_MODE)),
        () -> assertThrows(ConfigurationException.class,
            () -> new DatabaseConfig(HOST, PORT, DATABASE, value, PASSWORD, SSL_MODE)),
        () -> assertEquals(expectedPasswordError,
            assertThrows(ConfigurationException.class,
                () -> new DatabaseConfig(HOST, PORT, DATABASE, USERNAME, value, SSL_MODE))
                .getMessage()),
        () -> assertThrows(ConfigurationException.class,
            () -> new DatabaseConfig(HOST, PORT, DATABASE, USERNAME, PASSWORD, value)));
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 0, 65536})
  void shouldRejectInvalidPorts(final int port) {
    // Arrange
    final String expected = "Invalid or missing configuration property: DB_PORT.";

    // Act
    final ConfigurationException error = assertThrows(ConfigurationException.class,
        () -> new DatabaseConfig(HOST, port, DATABASE, USERNAME, PASSWORD, SSL_MODE));

    // Assert
    assertEquals(expected, error.getMessage());
  }

  @ParameterizedTest
  @ValueSource(strings = {"disable", "allow", "prefer", "require", "verify-ca", "verify-full"})
  void shouldPreserveSupportedSslModes(final String mode) {
    // Arrange
    final DatabaseConfig config = new DatabaseConfig(HOST, PORT, DATABASE, USERNAME, PASSWORD, mode);

    // Act
    final String url = config.buildJdbcUrl();

    // Assert
    assertEquals("jdbc:postgresql://localhost:5432/crud_usuarios?sslmode=" + mode, url);
  }

  @ParameterizedTest
  @ValueSource(strings = {"invalid", "REQUIRE", "require&password=test_secret"})
  void shouldRejectInvalidSslModeWithoutEchoingIt(final String mode) {
    // Arrange
    final String expected = "Invalid or missing configuration property: DB_SSLMODE.";

    // Act
    final ConfigurationException error = assertThrows(ConfigurationException.class,
        () -> new DatabaseConfig(HOST, PORT, DATABASE, USERNAME, PASSWORD, mode));

    // Assert
    assertEquals(expected, error.getMessage());
  }

  @Test
  void shouldEncodeDatabaseNameWithoutAddingJdbcOptions() {
    // Arrange
    final DatabaseConfig config =
        new DatabaseConfig(HOST, PORT, "demo?sslmode=disable", USERNAME, PASSWORD, SSL_MODE);

    // Act
    final String url = config.buildJdbcUrl();

    // Assert
    assertEquals("jdbc:postgresql://localhost:5432/demo%3Fsslmode%3Ddisable?sslmode=require", url);
  }

  @Test
  void shouldNotExposeCredentialsInStringRepresentation() {
    // Arrange
    final DatabaseConfig config = new DatabaseConfig(HOST, PORT, DATABASE, USERNAME, PASSWORD);

    // Act
    final String description = config.toString();

    // Assert
    assertAll(
        () -> assertFalse(description.contains(PASSWORD)),
        () -> assertFalse(description.contains(USERNAME)));
  }
}
