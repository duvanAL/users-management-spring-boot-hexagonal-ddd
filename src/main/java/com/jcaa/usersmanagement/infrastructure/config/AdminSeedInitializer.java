package com.jcaa.usersmanagement.infrastructure.config;

import com.jcaa.usersmanagement.domain.enums.UserRole;
import com.jcaa.usersmanagement.domain.enums.UserStatus;
import com.jcaa.usersmanagement.domain.exception.InvalidUserEmailException;
import com.jcaa.usersmanagement.domain.exception.InvalidUserPasswordException;
import com.jcaa.usersmanagement.domain.valueobject.UserEmail;
import com.jcaa.usersmanagement.domain.valueobject.UserPassword;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.seed.admin", name = "enabled", havingValue = "true")
@DependsOnDatabaseInitialization
public final class AdminSeedInitializer implements InitializingBean {
  private static final String PROP_EMAIL = "SEED_ADMIN_EMAIL";
  private static final String PROP_PASSWORD = "SEED_ADMIN_PASSWORD";
  private static final String ADMIN_NAME = "Administrador";
  private static final int MAX_EMAIL_LENGTH = 150;
  private static final int MAX_PASSWORD_BYTES = 72;
  private static final String INSERT_ADMIN =
      "INSERT INTO users (id, name, email, password, role, status) VALUES (?, ?, ?, ?, ?, ?) "
          + "ON CONFLICT (email) DO NOTHING";

  private final JdbcTemplate jdbcTemplate;
  private final String email;
  private final String password;

  public AdminSeedInitializer(
      final JdbcTemplate jdbcTemplate,
      @Value("${app.seed.admin.email:}") final String email,
      @Value("${app.seed.admin.password:}") final String password) {
    this.jdbcTemplate = jdbcTemplate;
    this.email = email;
    this.password = password;
  }

  @Override
  public void afterPropertiesSet() {
    final UserEmail validatedEmail = validateEmail();
    final UserPassword hashedPassword = hashPassword();
    jdbcTemplate.update(INSERT_ADMIN, UUID.randomUUID().toString(), ADMIN_NAME,
        validatedEmail.value(), hashedPassword.value(), UserRole.ADMIN.name(), UserStatus.ACTIVE.name());
  }

  private UserEmail validateEmail() {
    if (Objects.isNull(email) || email.isBlank() || email.trim().length() > MAX_EMAIL_LENGTH) {
      throw ConfigurationException.becauseInvalidProperty(PROP_EMAIL);
    }
    try {
      return new UserEmail(email);
    } catch (final InvalidUserEmailException error) {
      // La excepcion de dominio incluye el valor recibido; no propagar datos personales al log.
      throw ConfigurationException.becauseInvalidProperty(PROP_EMAIL);
    }
  }

  private UserPassword hashPassword() {
    if (Objects.isNull(password) || password.isBlank()
        || password.trim().getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
      throw ConfigurationException.becauseInvalidProperty(PROP_PASSWORD);
    }
    try {
      return UserPassword.fromPlainText(password);
    } catch (final InvalidUserPasswordException error) {
      throw ConfigurationException.becauseInvalidProperty(PROP_PASSWORD);
    }
  }
}
