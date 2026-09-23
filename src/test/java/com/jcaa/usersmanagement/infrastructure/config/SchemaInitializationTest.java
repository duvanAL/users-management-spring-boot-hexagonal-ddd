package com.jcaa.usersmanagement.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.sql.init.SqlInitializationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.ResourcePropertySource;

class SchemaInitializationTest {
  private static final String APPLICATION_PROPERTIES = "src/main/resources/application.properties";
  private static final String SQL_ERROR = "schema initialization rejected";

  private final DataSource dataSource = mock(DataSource.class);
  private final Connection connection = mock(Connection.class);
  private final Statement statement = mock(Statement.class);
  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(SqlInitializationAutoConfiguration.class))
      .withBean(DataSource.class, () -> dataSource)
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

  @BeforeEach
  void setUp() throws SQLException {
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.createStatement()).thenReturn(statement);
    when(connection.getAutoCommit()).thenReturn(true);
  }

  @Test
  void shouldApplyPackagedSchemaOnStartup() {
    // Arrange: use the production properties and schema, with a simulated JDBC connection.

    // Act
    runner.run(context -> {
      // Assert
      assertThat(context).hasNotFailed();
      verify(statement).execute(startsWith("CREATE TABLE IF NOT EXISTS users"));
    });
  }

  @Test
  void shouldFailStartupWhenSchemaIsMissing() {
    // Arrange
    final String missingSchema = "spring.sql.init.schema-locations=classpath:missing-schema.sql";

    // Act
    runner.withPropertyValues(missingSchema).run(context -> {
      // Assert
      assertThat(context).hasFailed();
      verify(statement, never()).execute(anyString());
    });
  }

  @Test
  void shouldFailStartupWhenSqlFails() throws SQLException {
    // Arrange
    when(statement.execute(anyString())).thenThrow(new SQLException(SQL_ERROR));

    // Act
    runner.run(context -> {
      // Assert
      assertThat(context).hasFailed();
      assertThat(context.getStartupFailure())
          .hasRootCauseInstanceOf(SQLException.class)
          .hasRootCauseMessage(SQL_ERROR);
    });
  }
}
