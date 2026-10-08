package com.jcaa.usersmanagement.infrastructure.messaging.kafka;

import com.jcaa.usersmanagement.application.port.out.EmailSenderPort;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Worker-side consumer that sends queued emails and publishes outcomes. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnExpression(
    "'${app.kafka.enabled:false}' == 'true' and '${app.runtime.role:api}' == 'notification-worker'")
public class KafkaNotificationWorker {

  private static final String FAILURE_CODE = "EMAIL_DELIVERY_FAILED";

  private final EmailSenderPort emailSenderPort;
  private final KafkaMessagePublisher publisher;
  private final KafkaNotificationProperties properties;

  @KafkaListener(
      topics = "${app.kafka.request-topic}",
      groupId = "${app.kafka.notify-group-id}",
      containerFactory = "notificationRequestListenerContainerFactory")
  public void onRequest(final NotificationRequestMessage request) {
    if (request.schemaVersion() != NotificationRequestMessage.CURRENT_SCHEMA_VERSION) {
      throw new IllegalArgumentException("Unsupported notification message schema version.");
    }

    NotificationResultMessage result;
    try {
      emailSenderPort.send(request.toEmailDestination());
      result = result(request, NotificationResultMessage.Status.SENT, null);
    } catch (final RuntimeException exception) {
      log.warn("No se pudo entregar la notificación. id={}", request.notificationId());
      final NotificationDeadLetterMessage deadLetter =
          new NotificationDeadLetterMessage(
              NotificationRequestMessage.CURRENT_SCHEMA_VERSION,
              request,
              FAILURE_CODE,
              Instant.now());
      publisher.publish(
          properties.deadLetterTopic(), request.notificationId().toString(), deadLetter);
      result = result(request, NotificationResultMessage.Status.FAILED, FAILURE_CODE);
    }

    publisher.publish(
        properties.resultTopic(), request.notificationId().toString(), result);
  }

  private static NotificationResultMessage result(
      final NotificationRequestMessage request,
      final NotificationResultMessage.Status status,
      final String failureCode) {
    return new NotificationResultMessage(
        NotificationRequestMessage.CURRENT_SCHEMA_VERSION,
        request.notificationId(),
        status,
        Instant.now(),
        failureCode);
  }
}
