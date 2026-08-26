package com.jcaa.usersmanagement.infrastructure.adapter.persistence.config;

public record DatabaseConfig(
    String host, int port, String databaseName, String username, String password, String sslMode) {
  public DatabaseConfig(
      final String host, final int port, final String databaseName,
      final String username, final String password) {
    this(host, port, databaseName, username, password, "require");
  }

  private static final String URL_TEMPLATE =
      "jdbc:postgresql://%s:%d/%s?sslmode=%s";

  public String buildJdbcUrl() {
    return String.format(URL_TEMPLATE, host, port, databaseName, sslMode);
  }
}
