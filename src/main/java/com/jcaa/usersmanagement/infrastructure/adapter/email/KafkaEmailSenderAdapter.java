package com.jcaa.usersmanagement.infrastructure.adapter.email;

import com.jcaa.usersmanagement.application.port.out.EmailSenderPort;
import com.jcaa.usersmanagement.domain.model.EmailDestinationModel;
import com.jcaa.usersmanagement.infrastructure.messaging.kafka.EmailOutboxRepository;
import com.jcaa.usersmanagement.infrastructure.messaging.kafka.NotificationRequestMessage;
import lombok.extern.slf4j.Slf4j;

/** Queues email requests for the independent notification worker. */
@Slf4j
public final class KafkaEmailSenderAdapter implements EmailSenderPort {

  private final EmailOutboxRepository outboxRepository;

  public KafkaEmailSenderAdapter(final EmailOutboxRepository outboxRepository) {
    this.outboxRepository = outboxRepository;
  }

  @Override
  public void send(final EmailDestinationModel destination) {
    final NotificationRequestMessage message = NotificationRequestMessage.from(destination);
    outboxRepository.enqueue(message);
    log.info(
        "[KafkaEmailSenderAdapter] Notificación guardada en outbox. id={}",
        message.notificationId());
  }
}
