package com.jcaa.usersmanagement.infrastructure.adapter.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import com.jcaa.usersmanagement.domain.enums.UserRole;
import com.jcaa.usersmanagement.domain.enums.UserStatus;
import com.jcaa.usersmanagement.domain.exception.UserNotFoundException;
import com.jcaa.usersmanagement.domain.model.UserModel;
import com.jcaa.usersmanagement.domain.valueobject.UserEmail;
import com.jcaa.usersmanagement.domain.valueobject.UserId;
import com.jcaa.usersmanagement.domain.valueobject.UserName;
import com.jcaa.usersmanagement.domain.valueobject.UserPassword;
import com.jcaa.usersmanagement.infrastructure.adapter.persistence.exception.PersistenceException;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.transaction.support.TransactionTemplate;
import com.jcaa.usersmanagement.infrastructure.messaging.kafka.EmailOutboxRepository;
import com.jcaa.usersmanagement.infrastructure.messaging.kafka.NotificationRequestMessage;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.postgresql.ds.PGSimpleDataSource;

@Testcontainers
class UserRepositoryPostgresIntegrationTest {

  @Container
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:17-alpine");

  private static DataSource dataSource;
  private UserRepositoryPostgres repository;

  @BeforeAll
  static void initializeSchema() {
    dataSource = createDataSource();
    new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
  }

  @BeforeEach
  void clearUsers() throws Exception {
    repository = new UserRepositoryPostgres(dataSource);
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("TRUNCATE TABLE users, email_notification_outbox");
    }
  }

  @Test
  void shouldSaveAndFindUserByIdAndEmail() {
    // Arrange
    final UserModel user = newUser("Ana Pérez", "ana@example.invalid");

    // Act
    final UserModel saved = repository.save(user);

    // Assert
    assertThat(saved.getId()).isEqualTo(user.getId());
    assertThat(saved.getName().value()).isEqualTo("Ana Pérez");
    assertThat(saved.getPassword().verifyPlain("Password987!")).isTrue();
    assertThat(repository.getById(user.getId())).contains(saved);
    assertThat(repository.getByEmail(user.getEmail())).contains(saved);
  }

  @Test
  void shouldUpdateExistingUser() {
    // Arrange
    final UserModel saved = repository.save(newUser("Ana Pérez", "ana@example.invalid"));
    final UserModel updated = new UserModel(
        saved.getId(),
        new UserName("Ana María Pérez"),
        new UserEmail("ana.maria@example.invalid"),
        UserPassword.fromPlainText("Different987!"),
        UserRole.REVIEWER,
        UserStatus.ACTIVE);

    // Act
    final UserModel result = repository.update(updated);

    // Assert
    assertThat(result.getName().value()).isEqualTo("Ana María Pérez");
    assertThat(result.getEmail()).isEqualTo(updated.getEmail());
    assertThat(result.getRole()).isEqualTo(UserRole.REVIEWER);
    assertThat(result.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(repository.getByEmail(new UserEmail("ana@example.invalid"))).isEmpty();
  }

  @Test
  void shouldListUsersInNameOrder() {
    // Arrange
    repository.save(newUser("Zoe User", "zoe@example.invalid"));
    repository.save(newUser("Ana User", "ana@example.invalid"));

    // Act
    final List<UserModel> users = repository.getAll();

    // Assert
    assertThat(users).extracting(user -> user.getName().value())
        .containsExactly("Ana User", "Zoe User");
  }

  @Test
  void shouldDeleteUser() {
    // Arrange
    final UserModel saved = repository.save(newUser("Ana Pérez", "ana@example.invalid"));

    // Act
    repository.delete(saved.getId());

    // Assert
    assertThat(repository.getById(saved.getId())).isEmpty();
    assertThat(repository.getAll()).isEmpty();
  }

  @Test
  void shouldRejectDuplicateEmailUsingPostgresUniqueConstraint() {
    // Arrange
    repository.save(newUser("Ana Pérez", "ana@example.invalid"));

    // Act & Assert
    assertThatThrownBy(() -> repository.save(newUser("Otra Persona", "ana@example.invalid")))
        .isInstanceOf(PersistenceException.class);
  }

  @Test
  void shouldKeepExistingDataWhenSchemaIsAppliedAgain() {
    // Arrange
    final UserModel saved = repository.save(newUser("Ana Pérez", "ana@example.invalid"));
    final ResourceDatabasePopulator schema =
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql"));

    // Act
    schema.execute(dataSource);
    schema.execute(dataSource);

    // Assert
    assertThat(repository.getById(saved.getId())).contains(saved);
  }

  @Test
  void shouldReturnEmptyForUnknownUser() {
    // Arrange & Act
    final Optional<UserModel> result = repository.getById(new UserId(UUID.randomUUID().toString()));

    // Assert
    assertThat(result).isEmpty();
    assertThatThrownBy(() -> repository.update(newUser("No Existe", "missing@example.invalid")))
        .isInstanceOf(UserNotFoundException.class);
  }

  @Test
  void shouldRollbackUserAndOutboxInsertAsOneTransaction() {
    final DataSource transactionAwareDataSource = new TransactionAwareDataSourceProxy(dataSource);
    final JdbcTemplate jdbcTemplate = new JdbcTemplate(transactionAwareDataSource);
    final EmailOutboxRepository outboxRepository = new EmailOutboxRepository(jdbcTemplate);
    final TransactionTemplate transaction =
        new TransactionTemplate(new DataSourceTransactionManager(transactionAwareDataSource));
    final String userId = UUID.randomUUID().toString();
    final NotificationRequestMessage notification =
        new NotificationRequestMessage(
            1,
            UUID.randomUUID(),
            Instant.now().truncatedTo(ChronoUnit.MICROS),
            "rollback@example.invalid",
            "Rollback",
            "Welcome",
            "<p>Welcome</p>");

    assertThatThrownBy(
            () ->
                transaction.execute(
                    status -> {
                      jdbcTemplate.update(
                          "INSERT INTO users (id, name, email, password, role, status) VALUES (?, ?, ?, ?, ?, ?)",
                          userId,
                          "Rollback",
                          "rollback@example.invalid",
                          "hashed",
                          "MEMBER",
                          "PENDING");
                      outboxRepository.enqueue(notification);
                      throw new IllegalStateException("force rollback");
                    }))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("force rollback");

    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE id = ?", Integer.class, userId))
        .isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM email_notification_outbox WHERE notification_id = ?",
                Integer.class,
                notification.notificationId()))
        .isZero();
  }

  @Test
  void shouldReadRetryAndDeleteOutboxEntries() {
    final JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
    final EmailOutboxRepository outboxRepository = new EmailOutboxRepository(jdbcTemplate);
    final NotificationRequestMessage notification =
        new NotificationRequestMessage(
            1,
            UUID.randomUUID(),
            Instant.now().truncatedTo(ChronoUnit.MICROS),
            "outbox@example.invalid",
            "Outbox",
            "Subject",
            "<p>Body</p>");
    outboxRepository.enqueue(notification);

    final EmailOutboxRepository.PendingEmailNotification pending =
        outboxRepository.lockNextDue().orElseThrow();
    assertThat(pending.message()).isEqualTo(notification);
    assertThat(pending.attempts()).isZero();

    outboxRepository.scheduleRetry(notification.notificationId(), Instant.now().plusSeconds(60));
    assertThat(outboxRepository.lockNextDue()).isEmpty();

    outboxRepository.markPublished(notification.notificationId());
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM email_notification_outbox WHERE notification_id = ?",
                Integer.class,
                notification.notificationId()))
        .isZero();
  }

  private static UserModel newUser(final String name, final String email) {
    return UserModel.create(
        new UserId(UUID.randomUUID().toString()),
        new UserName(name),
        new UserEmail(email),
        UserPassword.fromPlainText("Password987!"),
        UserRole.MEMBER);
  }

  private static DataSource createDataSource() {
    final PGSimpleDataSource dataSource = new PGSimpleDataSource();
    dataSource.setUrl("jdbc:postgresql://" + POSTGRES.getHost() + ":"
        + POSTGRES.getFirstMappedPort() + "/" + POSTGRES.getDatabaseName());
    dataSource.setUser(POSTGRES.getUsername());
    dataSource.setPassword(POSTGRES.getPassword());
    return dataSource;
  }
}
