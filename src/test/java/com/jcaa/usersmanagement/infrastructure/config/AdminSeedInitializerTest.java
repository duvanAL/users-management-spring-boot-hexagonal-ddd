package com.jcaa.usersmanagement.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jcaa.usersmanagement.domain.valueobject.UserPassword;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.ResourcePropertySource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

class AdminSeedInitializerTest {
  private static final String APPLICATION_PROPERTIES = "src/main/resources/application.properties";
  private static final String ENABLED = "SEED_ADMIN_ENABLED=true";
  private static final String EMAIL = "SEED_ADMIN_EMAIL=Seed.Admin@example.invalid";
  private static final String PASSWORD = "SEED_ADMIN_PASSWORD=SeedTest987!";
  private static final String PLAIN_PASSWORD = "SeedTest987!";

  private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withUserConfiguration(AdminSeedInitializer.class)
      .withBean(JdbcTemplate.class, () -> jdbcTemplate)
      .withInitializer(context -> {
        context.getEnvironment().getPropertySources()
            .remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        context.getEnvironment().getPropertySources()
            .remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        try {
          context.getEnvironment().getPropertySources().addLast(
              new ResourcePropertySource(new FileSystemResource(APPLICATION_PROPERTIES)));
        } catch (final IOException error) {
          throw new UncheckedIOException(error);
        }
      });

  @Test
  void shouldRemainDisabledWithoutCredentials() {
    // Arrange: production defaults leave the seed disabled and credentials empty.

    // Act
    runner.run(context -> {
      // Assert
      assertThat(context).hasNotFailed().doesNotHaveBean(AdminSeedInitializer.class);
      verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    });
  }

  @Test
  void shouldIgnoreInvalidCredentialsWhenDisabled() {
    // Arrange
    final String invalidEmail = "SEED_ADMIN_EMAIL=invalid";

    // Act
    runner.withPropertyValues("SEED_ADMIN_ENABLED=false", invalidEmail, "SEED_ADMIN_PASSWORD=x")
        .run(context -> {
          // Assert
          assertThat(context).hasNotFailed().doesNotHaveBean(AdminSeedInitializer.class);
          verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
        });
  }

  @Test
  void shouldInsertActiveAdminWithNormalizedEmailAndBcryptPassword() {
    // Arrange
    final ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);

    // Act
    runner.withPropertyValues(ENABLED, EMAIL, PASSWORD).run(context -> {
      // Assert
      assertThat(context).hasNotFailed().hasSingleBean(AdminSeedInitializer.class);
      verify(jdbcTemplate).update(anyString(), arguments.capture());
      final Object[] values = arguments.getValue();
      final String storedPassword = (String) values[3];
      assertAll(
          () -> assertEquals(4, UUID.fromString((String) values[0]).version()),
          () -> assertEquals("Administrador", values[1]),
          () -> assertEquals("seed.admin@example.invalid", values[2]),
          () -> assertNotEquals(PLAIN_PASSWORD, storedPassword),
          () -> assertTrue(storedPassword.startsWith("$2a$12$")),
          () -> assertTrue(UserPassword.fromHash(storedPassword).verifyPlain(PLAIN_PASSWORD)),
          () -> assertEquals("ADMIN", values[4]),
          () -> assertEquals("ACTIVE", values[5]));
    });
  }

  @ParameterizedTest
  @CsvSource({
      "SEED_ADMIN_EMAIL=, SEED_ADMIN_EMAIL",
      "SEED_ADMIN_EMAIL=not-an-email, SEED_ADMIN_EMAIL",
      "SEED_ADMIN_PASSWORD=, SEED_ADMIN_PASSWORD",
      "SEED_ADMIN_PASSWORD=short, SEED_ADMIN_PASSWORD"
  })
  void shouldRejectInvalidCredentialsWithoutEchoingValues(final String setting, final String property) {
    // Arrange
    final String expectedMessage = "Invalid or missing configuration property: " + property + ".";

    // Act
    runner.withPropertyValues(ENABLED, EMAIL, PASSWORD, setting).run(context -> {
      // Assert
      assertThat(context).hasFailed();
      assertThat(context.getStartupFailure())
          .hasRootCauseInstanceOf(ConfigurationException.class)
          .hasRootCauseMessage(expectedMessage);
      verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    });
  }

  @Test
  void shouldRejectPasswordsExceedingBcryptByteLimit() {
    // Arrange
    final String longPassword = "SEED_ADMIN_PASSWORD=" + "é".repeat(37);

    // Act
    runner.withPropertyValues(ENABLED, EMAIL, longPassword).run(context -> {
      // Assert
      assertThat(context).hasFailed();
      assertThat(context.getStartupFailure())
          .hasRootCauseMessage("Invalid or missing configuration property: SEED_ADMIN_PASSWORD.");
      verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    });
  }

  @Test
  void shouldRejectEmailExceedingColumnLength() {
    // Arrange
    final String longEmail = "SEED_ADMIN_EMAIL=" + "a".repeat(140) + "@example.invalid";

    // Act
    runner.withPropertyValues(ENABLED, longEmail, PASSWORD).run(context -> {
      // Assert
      assertThat(context).hasFailed();
      assertThat(context.getStartupFailure())
          .hasRootCauseMessage("Invalid or missing configuration property: SEED_ADMIN_EMAIL.");
      verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    });
  }

  @Test
  void shouldFailStartupWhenDatabaseWriteFails() {
    // Arrange
    when(jdbcTemplate.update(anyString(), any(Object[].class)))
        .thenThrow(new DataAccessResourceFailureException("Database unavailable"));

    // Act
    runner.withPropertyValues(ENABLED, EMAIL, PASSWORD).run(context -> {
      // Assert
      assertThat(context).hasFailed();
      assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(DataAccessResourceFailureException.class);
    });
  }
}
