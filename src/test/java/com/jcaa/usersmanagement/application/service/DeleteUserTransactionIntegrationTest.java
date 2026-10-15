package com.jcaa.usersmanagement.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcaa.events.jdbc.JdbcEventOutbox;
import com.jcaa.usersmanagement.application.port.in.DeleteUserUseCase;
import com.jcaa.usersmanagement.application.service.dto.command.DeleteUserCommand;
import com.jcaa.usersmanagement.domain.enums.UserRole;
import com.jcaa.usersmanagement.domain.enums.UserStatus;
import com.jcaa.usersmanagement.domain.model.UserModel;
import com.jcaa.usersmanagement.domain.valueobject.UserEmail;
import com.jcaa.usersmanagement.domain.valueobject.UserId;
import com.jcaa.usersmanagement.domain.valueobject.UserName;
import com.jcaa.usersmanagement.domain.valueobject.UserPassword;
import jakarta.validation.Validation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class DeleteUserTransactionIntegrationTest {
  @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");
  private DataSource dataSource;
  private JdbcTemplate jdbc;

  @Configuration(proxyBeanMethods = false)
  @EnableTransactionManagement
  static class Transactions {
    @Bean PlatformTransactionManager transactionManager(DataSource source) {
      return new DataSourceTransactionManager(source);
    }
  }

  @BeforeEach
  void database() throws Exception {
    dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("CREATE TABLE IF NOT EXISTS tx_users(id VARCHAR(36) PRIMARY KEY)");
    jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/V20261013_01__create_domain_event_outbox.sql")));
    jdbc.update("DELETE FROM domain_event_outbox");
    jdbc.update("DELETE FROM tx_users");
    jdbc.update("INSERT INTO tx_users(id) VALUES ('u-1')");
  }

  private void delete(boolean failAfterEnqueue) {
    var user = new UserModel(new UserId("u-1"), new UserName("Demo"), new UserEmail("demo@example.test"),
        UserPassword.fromPlainText("Demo-password-123!"), UserRole.MEMBER, UserStatus.ACTIVE);
    var outbox = new JdbcEventOutbox(jdbc, new ObjectMapper().findAndRegisterModules(), "domain_event_outbox");
    try (var factory = Validation.buildDefaultValidatorFactory();
         var context = new AnnotationConfigApplicationContext()) {
      context.registerBean(DataSource.class, () -> dataSource);
      context.register(Transactions.class);
      context.registerBean(DeleteUserService.class, () -> new DeleteUserService(
          id -> jdbc.update("DELETE FROM tx_users WHERE id = ?", id.value()),
          id -> Optional.of(user), factory.getValidator(), Optional.of(event -> {
            outbox.enqueue(event);
            if (failAfterEnqueue) throw new IllegalStateException("simulated publication intent failure");
          })));
      context.refresh();
      context.getBean(DeleteUserUseCase.class).execute(new DeleteUserCommand("u-1"));
    }
  }

  @Test
  void deleteAndEventCommitTogetherInPostgres() {
    delete(false);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM tx_users", Integer.class)).isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM domain_event_outbox", Integer.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT event_json FROM domain_event_outbox", String.class))
        .contains("user.deleted").doesNotContain("demo@example.test");
  }

  @Test
  void failureRollsBackDeletionAndOutboxInsert() {
    assertThatThrownBy(() -> delete(true)).isInstanceOf(IllegalStateException.class);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM tx_users", Integer.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM domain_event_outbox", Integer.class)).isZero();
  }
}
