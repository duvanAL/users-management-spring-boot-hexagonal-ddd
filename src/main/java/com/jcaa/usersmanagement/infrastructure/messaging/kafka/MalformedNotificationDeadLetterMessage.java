package com.jcaa.usersmanagement.infrastructure.messaging.kafka;

import java.time.Instant;
import java.util.Objects;

/** Safe metadata for a Kafka notification that could not be deserialized. */
public record MalformedNotificationDeadLetterMessage(
    int schemaVersion,
    String sourceTopic,
    int sourcePartition,
    long sourceOffset,
    String failureCode,
    Instant failedAt) {

  public static final int CURRENT_SCHEMA_VERSION = 1;

  public MalformedNotificationDeadLetterMessage {
    if (schemaVersion < 1) {
      throw new IllegalArgumentException("schemaVersion must be positive.");
    }
    if (Objects.isNull(sourceTopic) || sourceTopic.isBlank()) {
      throw new IllegalArgumentException("sourceTopic is required.");
    }
    if (sourcePartition < 0 || sourceOffset < 0) {
      throw new IllegalArgumentException("Kafka source coordinates must not be negative.");
    }
    if (Objects.isNull(failureCode) || failureCode.isBlank()) {
      throw new IllegalArgumentException("failureCode is required.");
    }
    Objects.requireNonNull(failedAt, "failedAt is required.");
  }
}
