package com.jcaa.usersmanagement.infrastructure.messaging.kafka;

import com.jcaa.usersmanagement.domain.model.EmailDestinationModel;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Versioned message placed on the notification request topic. */
public record NotificationRequestMessage(
    int schemaVersion,
    UUID notificationId,
    Instant occurredAt,
    String destinationEmail,
    String destinationName,
    String subject,
    String htmlContent) {

  public static final int CURRENT_SCHEMA_VERSION = 1;

  public NotificationRequestMessage {
    if (schemaVersion < 1) {
      throw new IllegalArgumentException("schemaVersion must be positive.");
    }
    Objects.requireNonNull(notificationId, "notificationId is required.");
    Objects.requireNonNull(occurredAt, "occurredAt is required.");
    requireText(destinationEmail, "destinationEmail");
    requireText(subject, "subject");
    Objects.requireNonNull(htmlContent, "htmlContent is required.");
  }

  public static NotificationRequestMessage from(final EmailDestinationModel destination) {
    return new NotificationRequestMessage(
        CURRENT_SCHEMA_VERSION,
        UUID.randomUUID(),
        Instant.now(),
        destination.getDestinationEmail(),
        destination.getDestinationName(),
        destination.getSubject(),
        destination.getBody());
  }

  public EmailDestinationModel toEmailDestination() {
    return new EmailDestinationModel(destinationEmail, destinationName, subject, htmlContent);
  }

  private static void requireText(final String value, final String field) {
    if (Objects.isNull(value) || value.isBlank()) {
      throw new IllegalArgumentException(field + " is required.");
    }
  }
}
