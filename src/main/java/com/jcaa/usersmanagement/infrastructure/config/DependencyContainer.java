package com.jcaa.usersmanagement.infrastructure.config;

import com.jcaa.usersmanagement.application.port.in.CreateUserUseCase;
import com.jcaa.usersmanagement.application.port.in.DeleteUserUseCase;
import com.jcaa.usersmanagement.application.port.in.GetAllUsersUseCase;
import com.jcaa.usersmanagement.application.port.in.GetUserByIdUseCase;
import com.jcaa.usersmanagement.application.port.in.LoginUseCase;
import com.jcaa.usersmanagement.application.port.in.UpdateUserUseCase;
import com.jcaa.usersmanagement.application.service.CreateUserService;
import com.jcaa.usersmanagement.application.service.DeleteUserService;
import com.jcaa.usersmanagement.application.service.EmailNotificationService;
import com.jcaa.usersmanagement.application.service.GetAllUsersService;
import com.jcaa.usersmanagement.application.service.GetUserByIdService;
import com.jcaa.usersmanagement.application.service.LoginService;
import com.jcaa.usersmanagement.application.service.UpdateUserService;
import com.jcaa.usersmanagement.infrastructure.adapter.email.JavaMailEmailSenderAdapter;
import com.jcaa.usersmanagement.infrastructure.adapter.email.GmailApiConfig;
import com.jcaa.usersmanagement.infrastructure.adapter.email.GmailApiEmailSenderAdapter;
import com.jcaa.usersmanagement.infrastructure.adapter.email.NoOpEmailSenderAdapter;
import com.jcaa.usersmanagement.infrastructure.adapter.email.SmtpConfig;
import com.jcaa.usersmanagement.infrastructure.adapter.persistence.config.DatabaseConfig;
import com.jcaa.usersmanagement.infrastructure.adapter.persistence.repository.UserRepositoryPostgres;
import com.jcaa.usersmanagement.infrastructure.entrypoint.desktop.controller.UserController;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.validation.Validator;
import javax.sql.DataSource;

public final class DependencyContainer {

  private static final String DB_HOST = "db.host";
  private static final String DB_PORT = "db.port";
  private static final String DB_NAME = "db.name";
  private static final String DB_USER = "db.username";
  private static final String DB_PASSWORD = "db.password";
  private static final String DB_SSLMODE = "db.sslmode";
  private static final String EMAIL_ENABLED = "app.email.enabled";
  private static final String EMAIL_PROVIDER = "app.email.provider";
  private static final String GMAIL_CLIENT_ID = "email.gmail.client-id";
  private static final String GMAIL_CLIENT_SECRET = "email.gmail.client-secret";
  private static final String GMAIL_REFRESH_TOKEN = "email.gmail.refresh-token";
  private static final String GMAIL_SENDER_ADDRESS = "email.gmail.sender-address";
  private static final String GMAIL_SENDER_NAME = "email.gmail.sender-name";

  private static final String SMTP_HOST = "smtp.host";
  private static final String SMTP_PORT = "smtp.port";
  private static final String SMTP_USER = "smtp.username";
  private static final String SMTP_PASSWORD = "smtp.password";
  private static final String SMTP_FROM = "smtp.from.address";
  private static final String SMTP_FROM_NAME = "smtp.from.name";

  private final UserController userController;

  public DependencyContainer() {
    final AppProperties properties = new AppProperties();

    final DataSource dataSource = buildDataSource(properties);
    final UserRepositoryPostgres userRepository = new UserRepositoryPostgres(dataSource);

    final com.jcaa.usersmanagement.application.port.out.EmailSenderPort emailSender =
        buildEmailSender(properties);
    final EmailNotificationService emailNotification = new EmailNotificationService(emailSender);

    // Construir Validator para las validaciones en la capa de aplicación
    final Validator validator = ValidatorProvider.buildValidator();

    final CreateUserUseCase createUserUseCase =
        new CreateUserService(userRepository, userRepository, emailNotification, validator);
    final UpdateUserUseCase updateUserUseCase =
        new UpdateUserService(userRepository, userRepository, userRepository, emailNotification, validator);
    final DeleteUserUseCase deleteUserUseCase =
        new DeleteUserService(userRepository, userRepository, validator);
    final GetUserByIdUseCase getUserByIdUseCase = new GetUserByIdService(userRepository, validator);
    final GetAllUsersUseCase getAllUsersUseCase = new GetAllUsersService(userRepository);
    final LoginUseCase loginUseCase = new LoginService(userRepository, validator);

    this.userController =
        new UserController(
            createUserUseCase,
            updateUserUseCase,
            deleteUserUseCase,
            getUserByIdUseCase,
            getAllUsersUseCase,
            loginUseCase);
  }

  public UserController userController() {
    return userController;
  }

  private static DataSource buildDataSource(final AppProperties properties) {
    final DatabaseConfig config =
        new DatabaseConfig(
            properties.get(DB_HOST),
            properties.getInt(DB_PORT),
            properties.get(DB_NAME),
            properties.get(DB_USER),
            properties.get(DB_PASSWORD),
            properties.get(DB_SSLMODE));
    final HikariDataSource ds = new HikariDataSource();
    ds.setJdbcUrl(config.buildJdbcUrl());
    ds.setUsername(config.username());
    ds.setPassword(config.password());
    return ds;
  }

  private static SmtpConfig buildSmtpConfig(final AppProperties properties) {
    return new SmtpConfig(
        properties.get(SMTP_HOST),
        properties.getInt(SMTP_PORT),
        properties.get(SMTP_USER),
        properties.get(SMTP_PASSWORD),
        properties.get(SMTP_FROM),
        properties.get(SMTP_FROM_NAME));
  }

  private static com.jcaa.usersmanagement.application.port.out.EmailSenderPort buildEmailSender(
      final AppProperties properties) {
    if (!properties.getBoolean(EMAIL_ENABLED)) {
      return new NoOpEmailSenderAdapter();
    }
    final String provider = properties.get(EMAIL_PROVIDER);
    if ("gmail".equalsIgnoreCase(provider)) {
      return new GmailApiEmailSenderAdapter(
          new GmailApiConfig(
              properties.get(GMAIL_CLIENT_ID),
              properties.get(GMAIL_CLIENT_SECRET),
              properties.get(GMAIL_REFRESH_TOKEN),
              properties.get(GMAIL_SENDER_ADDRESS),
              properties.get(GMAIL_SENDER_NAME)));
    }
    if ("smtp".equalsIgnoreCase(provider)) {
      return new JavaMailEmailSenderAdapter(buildSmtpConfig(properties));
    }
    throw new IllegalStateException("APP_EMAIL_PROVIDER must be either 'gmail' or 'smtp'.");
  }
}
