package com.jcaa.usersmanagement.infrastructure.adapter.email;

import com.jcaa.usersmanagement.application.port.out.EmailSenderPort;
import com.jcaa.usersmanagement.domain.exception.EmailSenderException;
import com.jcaa.usersmanagement.domain.model.EmailDestinationModel;
import com.jcaa.usersmanagement.infrastructure.messaging.kafka.KafkaMessagePublisher;
import com.jcaa.usersmanagement.infrastructure.messaging.kafka.KafkaNotificationProperties;
import com.jcaa.usersmanagement.infrastructure.messaging.kafka.NotificationRequestMessage;
import lombok.extern.slf4j.Slf4j;

/** Queues email requests for the independent notification worker. */
@Slf4j
public final class KafkaEmailSenderAdapter implements EmailSenderPort {

  private final KafkaMessagePublisher publisher;
  private final KafkaNotificationProperties properties;

  public KafkaEmailSenderAdapter(
      final KafkaMessagePublisher publisher, final KafkaNotificationProperties properties) {
    this.publisher = publisher;
    this.properties = properties;
  }

  @Override
  public void send(final EmailDestinationModel destination) {
    final NotificationRequestMessage message = NotificationRequestMessage.from(destination);
    try {
      publisher.publish(
          properties.requestTopic(), message.notificationId().toString(), message);
      log.info("[KafkaEmailSenderAdapter] Notificación encolada. id={}", message.notificationId());
    } catch (final RuntimeException exception) {
      throw EmailSenderException.becauseSendFailed(
          new IllegalStateException("No fue posible confirmar la publicación en Kafka."));
    }
  }
}
