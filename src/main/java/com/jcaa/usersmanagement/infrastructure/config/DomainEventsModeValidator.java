package com.jcaa.usersmanagement.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DomainEventsModeValidator {
  public DomainEventsModeValidator(
      @Value("${app.domain-events.enabled:false}") boolean events,
      @Value("${app.kafka.enabled:false}") boolean kafka,
      @Value("${app.kafka.in-process-worker-enabled:false}") boolean embeddedWorker) {
    if (events && (!kafka || embeddedWorker))
      throw new IllegalStateException(
          "Domain events require Kafka and a separate notification service; disable the in-process"
              + " worker.");
  }
}
