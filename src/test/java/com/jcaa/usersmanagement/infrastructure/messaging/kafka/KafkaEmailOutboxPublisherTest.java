package com.jcaa.usersmanagement.infrastructure.messaging.kafka;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KafkaEmailOutboxPublisherTest {

  @Mock private EmailOutboxRepository outboxRepository;
  @Mock private KafkaMessagePublisher kafkaPublisher;

  @Test
  void shouldPublishAndDeleteTheOutboxRecordAfterBrokerAcknowledgement() {
    final NotificationRequestMessage message = message();
    when(outboxRepository.lockNextDue())
        .thenReturn(Optional.of(new EmailOutboxRepository.PendingEmailNotification(message, 0)));

    publisher().publishNext();

    verify(kafkaPublisher).publish("user.notification.requested", message.notificationId().toString(), message);
    verify(outboxRepository).markPublished(message.notificationId());
    verify(outboxRepository, never()).scheduleRetry(any(), any());
  }

  @Test
  void shouldKeepRecordAndScheduleRetryWhenKafkaIsUnavailable() {
    final NotificationRequestMessage message = message();
    when(outboxRepository.lockNextDue())
        .thenReturn(Optional.of(new EmailOutboxRepository.PendingEmailNotification(message, 2)));
    org.mockito.Mockito.doThrow(new IllegalStateException("broker unavailable"))
        .when(kafkaPublisher)
        .publish("user.notification.requested", message.notificationId().toString(), message);

    publisher().publishNext();

    verify(outboxRepository).scheduleRetry(eq(message.notificationId()), any(Instant.class));
    verify(outboxRepository, never()).markPublished(message.notificationId());
  }

  @Test
  void shouldNotCallKafkaWhenThereIsNoDueRecord() {
    when(outboxRepository.lockNextDue()).thenReturn(Optional.empty());

    publisher().publishNext();

    verify(kafkaPublisher, never()).publish(any(), any(), any());
  }

  private KafkaEmailOutboxPublisher publisher() {
    return new KafkaEmailOutboxPublisher(outboxRepository, kafkaPublisher, properties());
  }

  private static NotificationRequestMessage message() {
    return new NotificationRequestMessage(
        1, UUID.randomUUID(), Instant.now(), "ada@example.com", "Ada", "Subject", "<p>Body</p>");
  }

  private static KafkaNotificationProperties properties() {
    return new KafkaNotificationProperties(
        true,
        "localhost:9092",
        "users-api",
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
