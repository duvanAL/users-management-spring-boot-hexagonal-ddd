package com.jcaa.usersmanagement.infrastructure.messaging.kafka;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;

/** Durable PostgreSQL outbox for email notifications that must eventually reach Kafka. */
@RequiredArgsConstructor
public class EmailOutboxRepository {

  private static final String INSERT =
      "INSERT INTO email_notification_outbox "
          + "(notification_id, occurred_at, destination_email, destination_name, subject, html_content) "
          + "VALUES (?, ?, ?, ?, ?, ?)";

  private static final String SELECT_NEXT_DUE =
      "SELECT notification_id, occurred_at, destination_email, destination_name, subject, html_content, attempts "
          + "FROM email_notification_outbox "
          + "WHERE next_attempt_at <= CURRENT_TIMESTAMP "
          + "ORDER BY created_at ASC "
          + "LIMIT 1 FOR UPDATE SKIP LOCKED";

  private static final String DELETE_PUBLISHED =
      "DELETE FROM email_notification_outbox WHERE notification_id = ?";

  private static final String SCHEDULE_RETRY =
      "UPDATE email_notification_outbox "
          + "SET attempts = attempts + 1, next_attempt_at = ? "
          + "WHERE notification_id = ?";

  private final JdbcTemplate jdbcTemplate;

  public void enqueue(final NotificationRequestMessage message) {
    jdbcTemplate.update(
        INSERT,
        message.notificationId(),
        Timestamp.from(message.occurredAt()),
        message.destinationEmail(),
        message.destinationName(),
        message.subject(),
        message.htmlContent());
  }

  public Optional<PendingEmailNotification> lockNextDue() {
    return jdbcTemplate.query(SELECT_NEXT_DUE, EmailOutboxRepository::mapPending).stream().findFirst();
  }

  public void markPublished(final UUID notificationId) {
    jdbcTemplate.update(DELETE_PUBLISHED, notificationId);
  }

  public void scheduleRetry(final UUID notificationId, final Instant nextAttemptAt) {
    jdbcTemplate.update(SCHEDULE_RETRY, Timestamp.from(nextAttemptAt), notificationId);
  }

  private static PendingEmailNotification mapPending(final ResultSet resultSet, final int rowNumber)
      throws SQLException {
    final NotificationRequestMessage message =
        new NotificationRequestMessage(
            NotificationRequestMessage.CURRENT_SCHEMA_VERSION,
            UUID.fromString(resultSet.getString("notification_id")),
            resultSet.getTimestamp("occurred_at").toInstant(),
            resultSet.getString("destination_email"),
            resultSet.getString("destination_name"),
            resultSet.getString("subject"),
            resultSet.getString("html_content"));
    return new PendingEmailNotification(message, resultSet.getInt("attempts"));
  }

  public record PendingEmailNotification(NotificationRequestMessage message, int attempts) {}
}
