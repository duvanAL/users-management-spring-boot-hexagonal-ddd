package com.jcaa.usersmanagement.infrastructure.messaging.kafka;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;

/** Sends Kafka records and waits for broker acknowledgement within a finite timeout. */
@RequiredArgsConstructor
public class KafkaMessagePublisher {

  private final KafkaTemplate<String, Object> kafkaTemplate;
  private final KafkaNotificationProperties properties;

  public void publish(final String topic, final String key, final Object message) {
    try {
      kafkaTemplate
          .send(topic, key, message)
          .get(properties.sendTimeoutMs(), TimeUnit.MILLISECONDS);
    } catch (final InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while awaiting Kafka acknowledgement.", exception);
    } catch (final ExecutionException | TimeoutException exception) {
      throw new IllegalStateException("Kafka did not acknowledge the message in time.", exception);
    }
  }
}
