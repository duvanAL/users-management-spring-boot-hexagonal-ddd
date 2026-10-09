package com.jcaa.usersmanagement.infrastructure.messaging.kafka;

import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.kafka")
public record KafkaNotificationProperties(
    boolean enabled,
    String bootstrapServers,
    String username,
    String password,
    String securityProtocol,
    String saslMechanism,
    String requestTopic,
    String resultTopic,
    String deadLetterTopic,
    String usersGroupId,
    String notifyGroupId,
    int sendTimeoutMs) {

  public KafkaNotificationProperties {
    if (enabled) {
      requireText(bootstrapServers, "KAFKA_BOOTSTRAP_SERVERS");
      requireText(username, "KAFKA_USERNAME");
      requireText(password, "KAFKA_PASSWORD");
      requireText(securityProtocol, "KAFKA_SECURITY_PROTOCOL");
      requireText(saslMechanism, "KAFKA_SASL_MECHANISM");
      requireText(requestTopic, "KAFKA_TOPIC_NOTIFICATION_REQUESTED");
      requireText(resultTopic, "KAFKA_TOPIC_NOTIFICATION_RESULT");
      requireText(deadLetterTopic, "KAFKA_TOPIC_NOTIFICATION_DLQ");
      requireText(usersGroupId, "KAFKA_USERS_GROUP_ID");
      requireText(notifyGroupId, "KAFKA_NOTIFY_GROUP_ID");
      if (sendTimeoutMs < 1) {
        throw new IllegalArgumentException("KAFKA_SEND_TIMEOUT_MS must be positive.");
      }
    }
  }

  private static void requireText(final String value, final String property) {
    if (Objects.isNull(value) || value.isBlank()) {
      throw new IllegalArgumentException(property + " is required when Kafka is enabled.");
    }
  }

  @Override
  public String toString() {
    return "KafkaNotificationProperties[enabled="
        + enabled
        + ", bootstrapServers="
        + bootstrapServers
        + ", username="
        + username
        + ", password=[REDACTED], securityProtocol="
        + securityProtocol
        + ", saslMechanism="
        + saslMechanism
        + ", requestTopic="
        + requestTopic
        + ", resultTopic="
        + resultTopic
        + ", deadLetterTopic="
        + deadLetterTopic
        + ", usersGroupId="
        + usersGroupId
        + ", notifyGroupId="
        + notifyGroupId
        + ", sendTimeoutMs="
        + sendTimeoutMs
        + "]";
  }
}
