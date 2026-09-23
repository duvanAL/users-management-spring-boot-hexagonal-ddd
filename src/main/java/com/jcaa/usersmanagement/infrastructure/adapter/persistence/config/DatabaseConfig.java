package com.jcaa.usersmanagement.infrastructure.adapter.persistence.config;

import com.jcaa.usersmanagement.infrastructure.config.ConfigurationException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;

public record DatabaseConfig(
    String host, int port, String databaseName, String username, String password, String sslMode) {
  private static final Set<String> SSL_MODES =
      Set.of("disable", "allow", "prefer", "require", "verify-ca", "verify-full");
  private static final int MAX_PORT = 65_535;
  private static final String PROP_HOST = "DB_HOST";
  private static final String PROP_PORT = "DB_PORT";
  private static final String PROP_NAME = "DB_NAME";
  private static final String PROP_USERNAME = "DB_USERNAME";
  private static final String PROP_PASSWORD = "DB_PASSWORD";
  private static final String PROP_SSLMODE = "DB_SSLMODE";
  private static final String REDACTED_CONFIG = "DatabaseConfig[values=redacted]";

  public DatabaseConfig {
    requireValue(host, PROP_HOST);
    requireValue(databaseName, PROP_NAME);
    requireValue(username, PROP_USERNAME);
    requireValue(password, PROP_PASSWORD);
    requireValue(sslMode, PROP_SSLMODE);
    if (port < 1 || port > MAX_PORT) {
      throw ConfigurationException.becauseInvalidProperty(PROP_PORT);
    }
    if (!SSL_MODES.contains(sslMode)) {
      throw ConfigurationException.becauseInvalidProperty(PROP_SSLMODE);
    }
  }

  public DatabaseConfig(
      final String host, final int port, final String databaseName,
      final String username, final String password) {
    this(host, port, databaseName, username, password, "require");
  }

  private static final String URL_TEMPLATE =
      "jdbc:postgresql://%s:%d/%s?sslmode=%s";

  public String buildJdbcUrl() {
    return String.format(URL_TEMPLATE, host, port,
        URLEncoder.encode(databaseName, StandardCharsets.UTF_8), sslMode);
  }

  @Override
  public String toString() {
    return REDACTED_CONFIG;
  }

  private static void requireValue(final String value, final String property) {
    if (Objects.isNull(value) || value.isBlank()) {
      throw ConfigurationException.becauseInvalidProperty(property);
    }
  }
}
