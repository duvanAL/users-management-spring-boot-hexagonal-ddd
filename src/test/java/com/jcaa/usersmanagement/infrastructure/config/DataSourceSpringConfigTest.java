package com.jcaa.usersmanagement.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mockConstruction;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedConstruction;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.ResourcePropertySource;

class DataSourceSpringConfigTest {
  private static final String APPLICATION_PROPERTIES = "src/main/resources/application.properties";
  private static final String PASSWORD_PROPERTY = "DB_PASSWORD=test_secret";

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withUserConfiguration(DataSourceSpringConfig.class)
      .withInitializer(context -> {
        context.getEnvironment().getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        context.getEnvironment().getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        try {
          context.getEnvironment().getPropertySources().addLast(
              new ResourcePropertySource(new FileSystemResource(APPLICATION_PROPERTIES)));
        } catch (final IOException error) {
          throw new UncheckedIOException(error);
        }
      });

  @Test
  void shouldResolveRemoteEnvironmentSettingsIntoThePool() {
    // Arrange
    final List<HikariConfig> captured = new ArrayList<>();
    try (final MockedConstruction<HikariDataSource> pools = mockConstruction(
        HikariDataSource.class, (pool, context) -> captured.add((HikariConfig) context.arguments().get(0)))) {
      // Act
      runner.withPropertyValues(PASSWORD_PROPERTY, "DB_HOST=db.example.test", "DB_PORT=5433",
          "DB_NAME=remote_users", "DB_USERNAME=remote_user", "DB_SSLMODE=verify-full",
          "DB_POOL_MAX_SIZE=3", "DB_POOL_MIN_IDLE=0", "DB_CONNECTION_TIMEOUT_MS=5000")
          .run(context -> {
            // Assert
            assertThat(context).hasNotFailed();
            assertEquals(1, pools.constructed().size());
            final HikariConfig config = captured.get(0);
            assertAll(
                () -> assertEquals("jdbc:postgresql://db.example.test:5433/remote_users?sslmode=verify-full",
                    config.getJdbcUrl()),
                () -> assertEquals("remote_user", config.getUsername()),
                () -> assertEquals("test_secret", config.getPassword()),
                () -> assertEquals(3, config.getMaximumPoolSize()),
                () -> assertEquals(0, config.getMinimumIdle()),
                () -> assertEquals(5000, config.getConnectionTimeout()));
          });
    }
  }

  @Test
  void shouldUseLocalDefaultsWithAnExplicitPassword() {
    // Arrange
    final List<HikariConfig> captured = new ArrayList<>();
    try (final MockedConstruction<HikariDataSource> pools = mockConstruction(
        HikariDataSource.class, (pool, context) -> captured.add((HikariConfig) context.arguments().get(0)))) {
      // Act
      runner.withPropertyValues(PASSWORD_PROPERTY).run(context -> {
        // Assert
        assertThat(context).hasNotFailed();
        assertEquals(1, pools.constructed().size());
        final HikariConfig config = captured.get(0);
        assertAll(
            () -> assertEquals("jdbc:postgresql://localhost:5432/crud_usuarios?sslmode=disable",
                config.getJdbcUrl()),
            () -> assertEquals(5, config.getMaximumPoolSize()),
            () -> assertEquals(1, config.getMinimumIdle()),
            () -> assertEquals(30000, config.getConnectionTimeout()));
      });
    }
  }

  @ParameterizedTest
  @CsvSource({
      "DB_PASSWORD=, DB_PASSWORD",
      "DB_PORT=0, DB_PORT",
      "DB_SSLMODE=invalid, DB_SSLMODE",
      "DB_POOL_MAX_SIZE=0, DB_POOL_MAX_SIZE",
      "DB_POOL_MIN_IDLE=-1, DB_POOL_MIN_IDLE",
      "DB_POOL_MIN_IDLE=6, DB_POOL_MIN_IDLE",
      "DB_CONNECTION_TIMEOUT_MS=249, DB_CONNECTION_TIMEOUT_MS"
  })
  void shouldRejectInvalidSettingsBeforeOpeningConnections(final String setting, final String property) {
    // Arrange
    try (final MockedConstruction<HikariDataSource> pools = mockConstruction(HikariDataSource.class)) {
      // Act
      runner.withPropertyValues(PASSWORD_PROPERTY, setting).run(context -> {
        // Assert
        assertThat(context).hasFailed();
        assertThat(context.getStartupFailure())
            .hasRootCauseInstanceOf(ConfigurationException.class)
            .hasRootCauseMessage("Invalid or missing configuration property: " + property + ".");
        assertThat(pools.constructed()).isEmpty();
      });
    }
  }
}
