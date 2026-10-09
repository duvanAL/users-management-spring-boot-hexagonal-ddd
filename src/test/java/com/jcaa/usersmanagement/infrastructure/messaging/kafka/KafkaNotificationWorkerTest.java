package com.jcaa.usersmanagement.infrastructure.messaging.kafka;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.jcaa.usersmanagement.application.port.out.EmailSenderPort;
import com.jcaa.usersmanagement.domain.exception.EmailSenderException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KafkaNotificationWorkerTest {

  @Mock private EmailSenderPort emailSenderPort;
  @Mock private KafkaMessagePublisher publisher;

  @Test
  void shouldSendEmailThenPublishSuccessfulResult() {
    final KafkaNotificationProperties properties = properties();
    final KafkaNotificationWorker worker =
        new KafkaNotificationWorker(emailSenderPort, publisher, properties);
    final NotificationRequestMessage request = request();

    worker.onRequest(request);

    verify(emailSenderPort)
        .send(request.toEmailDestination(), request.notificationId().toString());
    final ArgumentCaptor<NotificationResultMessage> resultCaptor =
        ArgumentCaptor.forClass(NotificationResultMessage.class);
    verify(publisher)
        .publish(eq(properties.resultTopic()), eq(request.notificationId().toString()), resultCaptor.capture());
    org.assertj.core.api.Assertions.assertThat(resultCaptor.getValue().status())
        .isEqualTo(NotificationResultMessage.Status.SENT);
    org.assertj.core.api.Assertions.assertThat(resultCaptor.getValue().notificationId())
        .isEqualTo(request.notificationId());
    verify(publisher, never()).publish(eq(properties.deadLetterTopic()), any(), any());
  }

  @Test
  void shouldDeadLetterFailedEmailAndPublishFailureResultWithoutProviderDetails() {
    final KafkaNotificationProperties properties = properties();
    final KafkaNotificationWorker worker =
        new KafkaNotificationWorker(emailSenderPort, publisher, properties);
    final NotificationRequestMessage request = request();
    doThrow(EmailSenderException.becauseSendFailed(new IllegalStateException("private provider data")))
        .when(emailSenderPort)
        .send(any(), any());

    worker.onRequest(request);

    final ArgumentCaptor<NotificationDeadLetterMessage> deadLetterCaptor =
        ArgumentCaptor.forClass(NotificationDeadLetterMessage.class);
    verify(publisher)
        .publish(
            eq(properties.deadLetterTopic()),
            eq(request.notificationId().toString()),
            deadLetterCaptor.capture());
    org.assertj.core.api.Assertions.assertThat(deadLetterCaptor.getValue().failureCode())
        .isEqualTo("EMAIL_DELIVERY_FAILED");
    org.assertj.core.api.Assertions.assertThat(deadLetterCaptor.getValue().toString())
        .doesNotContain("private provider data");

    final ArgumentCaptor<NotificationResultMessage> resultCaptor =
        ArgumentCaptor.forClass(NotificationResultMessage.class);
    verify(publisher)
        .publish(
            eq(properties.resultTopic()),
            eq(request.notificationId().toString()),
            resultCaptor.capture());
    org.assertj.core.api.Assertions.assertThat(resultCaptor.getValue().status())
        .isEqualTo(NotificationResultMessage.Status.FAILED);
    org.assertj.core.api.Assertions.assertThat(resultCaptor.getValue().failureCode())
        .isEqualTo("EMAIL_DELIVERY_FAILED");
  }

  @Test
  void shouldRejectUnsupportedMessageSchemaBeforeSendingEmail() {
    final KafkaNotificationWorker worker =
        new KafkaNotificationWorker(emailSenderPort, publisher, properties());
    final NotificationRequestMessage unsupported =
        new NotificationRequestMessage(2, UUID.randomUUID(), Instant.now(),
            "ada@example.com", "Ada", "Subject", "<p>Body</p>");

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> worker.onRequest(unsupported))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unsupported notification message schema version.");
    verifyNoMoreInteractions(emailSenderPort, publisher);
  }

  private static NotificationRequestMessage request() {
    return new NotificationRequestMessage(
        1,
        UUID.randomUUID(),
        Instant.now(),
        "ada@example.com",
        "Ada",
        "Account updated",
        "<p>Updated</p>");
  }

  private static KafkaNotificationProperties properties() {
    return new KafkaNotificationProperties(
        true,
        "localhost:9092",
        "notify-service",
        "fake-password",
        "SASL_SSL",
        "SCRAM-SHA-256",
        "user.notification.requested",
        "user.notification.result",
        "user.notification.dlq",
        "users-api",
        "notify-service",
        1000);
  }
}
