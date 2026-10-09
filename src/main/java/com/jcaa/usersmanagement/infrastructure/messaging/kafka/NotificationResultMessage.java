package com.jcaa.usersmanagement.infrastructure.messaging.kafka;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Outcome emitted by the notification worker after attempting delivery. */
public record NotificationResultMessage(
    int schemaVersion,
    UUID notificationId,
    Status status,
    Instant processedAt,
    String failureCode) {

  public NotificationResultMessage {
    if (schemaVersion < 1) {
      throw new IllegalArgumentException("schemaVersion must be positive.");
    }
    Objects.requireNonNull(notificationId, "notificationId is required.");
    Objects.requireNonNull(status, "status is required.");
    Objects.requireNonNull(processedAt, "processedAt is required.");
    if (status == Status.SENT && Objects.nonNull(failureCode)) {
      throw new IllegalArgumentException("Successful results must not include a failure code.");
    }
  }

  public enum Status {
    SENT,
    FAILED
  }
}
