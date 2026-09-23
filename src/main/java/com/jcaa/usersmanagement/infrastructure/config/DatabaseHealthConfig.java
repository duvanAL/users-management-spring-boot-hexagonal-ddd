package com.jcaa.usersmanagement.infrastructure.config;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class DatabaseHealthConfig {

  @Bean(name = "dbHealthIndicator")
  HealthIndicator databaseHealthIndicator(final DataSource dataSource) {
    return () -> {
      try (Connection connection = dataSource.getConnection()) {
        return connection.isValid(2) ? Health.up().build() : Health.down().build();
      } catch (final SQLException exception) {
        return Health.down().build();
      }
    };
  }
}
