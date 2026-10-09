package com.jcaa.usersmanagement.infrastructure.messaging.kafka;

import java.time.Instant;
import java.util.Objects;

/** Restricted dead-letter record; failure details intentionally exclude provider responses. */
public record NotificationDeadLetterMessage(
    int schemaVersion,
    NotificationRequestMessage request,
    String failureCode,
    Instant failedAt) {

  public NotificationDeadLetterMessage {
    if (schemaVersion < 1) {
      throw new IllegalArgumentException("schemaVersion must be positive.");
    }
    Objects.requireNonNull(request, "request is required.");
    Objects.requireNonNull(failedAt, "failedAt is required.");
    if (Objects.isNull(failureCode) || failureCode.isBlank()) {
      throw new IllegalArgumentException("failureCode is required.");
    }
  }
}
