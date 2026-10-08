package com.jcaa.usersmanagement.infrastructure.messaging.kafka;

import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

/** Publishes one pending outbox record per tick and retries it with bounded exponential backoff. */
@Slf4j
@RequiredArgsConstructor
public class KafkaEmailOutboxPublisher {

  private static final long INITIAL_BACKOFF_SECONDS = 5;
  private static final long MAX_BACKOFF_SECONDS = 900;

  private final EmailOutboxRepository outboxRepository;
  private final KafkaMessagePublisher kafkaPublisher;
  private final KafkaNotificationProperties properties;

  @Scheduled(fixedDelayString = "${app.kafka.outbox.poll-interval-ms:5000}")
  @Transactional
  public void publishNext() {
    try {
      outboxRepository
          .lockNextDue()
          .ifPresent(
              pending -> {
                final NotificationRequestMessage message = pending.message();
                try {
                  kafkaPublisher.publish(
                      properties.requestTopic(), message.notificationId().toString(), message);
                  outboxRepository.markPublished(message.notificationId());
                  log.info("[KafkaEmailOutboxPublisher] Notificación publicada. id={}", message.notificationId());
                } catch (final RuntimeException exception) {
                  final int attempts = pending.attempts() + 1;
                  final Instant nextAttemptAt = Instant.now().plus(backoff(attempts));
                  outboxRepository.scheduleRetry(message.notificationId(), nextAttemptAt);
                  log.warn(
                      "[KafkaEmailOutboxPublisher] Kafka no confirmó notificación; se reintentará. id={} attempt={} error={}",
                      message.notificationId(),
                      attempts,
                      exception.getClass().getSimpleName());
                }
              });
    } catch (final RuntimeException exception) {
      log.warn(
          "[KafkaEmailOutboxPublisher] No fue posible consultar/procesar el outbox. error={}",
          exception.getClass().getSimpleName());
    }
  }

  private static Duration backoff(final int attempts) {
    final int exponent = Math.min(Math.max(attempts - 1, 0), 20);
    final long seconds = Math.min(INITIAL_BACKOFF_SECONDS * (1L << exponent), MAX_BACKOFF_SECONDS);
    return Duration.ofSeconds(seconds);
  }
}
