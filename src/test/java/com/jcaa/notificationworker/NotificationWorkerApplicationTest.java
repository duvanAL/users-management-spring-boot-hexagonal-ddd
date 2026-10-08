package com.jcaa.notificationworker;

import static org.assertj.core.api.Assertions.assertThat;

import com.jcaa.usersmanagement.application.port.out.EmailSenderPort;
import com.jcaa.usersmanagement.infrastructure.adapter.email.BrevoEmailSenderAdapter;
import com.jcaa.usersmanagement.infrastructure.messaging.kafka.KafkaNotificationWorker;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class NotificationWorkerApplicationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(NotificationWorkerApplication.class)
          .withPropertyValues(
              "app.runtime.role=notification-worker",
              "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
              "app.kafka.enabled=true",
              "app.kafka.listener-auto-startup=false",
              "app.kafka.bootstrap-servers=kafka.example.test:10286",
              "app.kafka.username=notify-service",
              "app.kafka.password=fake-password",
              "app.kafka.security-protocol=SASL_SSL",
              "app.kafka.sasl-mechanism=SCRAM-SHA-256",
              "app.kafka.request-topic=user.notification.requested",
              "app.kafka.result-topic=user.notification.result",
              "app.kafka.dead-letter-topic=user.notification.dlq",
              "app.kafka.users-group-id=users-api",
              "app.kafka.notify-group-id=notify-service",
              "app.kafka.send-timeout-ms=1000",
              "app.email.enabled=true",
              "app.email.provider=brevo",
              "brevo.api-key=fake-api-key",
              "brevo.from.address=no-reply@example.test",
              "brevo.from.name=Users API",
              "smtp.host=",
              "smtp.port=587",
              "smtp.username=",
              "smtp.password=",
              "smtp.from.address=",
              "smtp.from.name=");

  @Test
  void shouldStartWorkerContextWithoutLoadingTheApiOrDatabase() {
    runner.run(
        context -> {
          assertThat(context).hasNotFailed().hasSingleBean(KafkaNotificationWorker.class);
          assertThat(context).hasSingleBean(EmailSenderPort.class);
          assertThat(context.getBean(EmailSenderPort.class))
              .isInstanceOf(BrevoEmailSenderAdapter.class);
          assertThat(context).doesNotHaveBean(DataSource.class);
        });
  }
}
