package com.jcaa.usersmanagement.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.jcaa.usersmanagement.application.port.out.EmailSenderPort;
import com.jcaa.usersmanagement.domain.model.EmailDestinationModel;
import com.jcaa.usersmanagement.infrastructure.adapter.email.NoOpEmailSenderAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SmtpSpringConfigTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withUserConfiguration(SmtpSpringConfig.class)
      .withPropertyValues(
          "app.email.enabled=false",
          "smtp.host=",
          "smtp.port=587",
          "smtp.username=",
          "smtp.password=",
          "smtp.from.address=",
          "smtp.from.name=");

  @Test
  void shouldStartWithoutSmtpCredentialsAndUseNoOpSenderWhenEmailIsDisabled() {
    // Arrange & Act
    runner.run(context -> {
      // Assert
      assertThat(context).hasNotFailed().hasSingleBean(EmailSenderPort.class);
      assertThat(context.getBean(EmailSenderPort.class))
          .isInstanceOf(NoOpEmailSenderAdapter.class);

      // Sending with the disabled adapter must not open an SMTP connection.
      context.getBean(EmailSenderPort.class).send(
          new EmailDestinationModel("user@example.invalid", "Usuario", "Asunto", "Mensaje"));
    });
  }
}
