package com.jcaa.usersmanagement.infrastructure.config;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcaa.usersmanagement.infrastructure.messaging.kafka.KafkaMessagePublisher;
import com.jcaa.usersmanagement.infrastructure.messaging.kafka.KafkaNotificationProperties;
import com.jcaa.usersmanagement.infrastructure.messaging.kafka.MalformedNotificationDeadLetterMessage;
import com.jcaa.usersmanagement.infrastructure.messaging.kafka.NotificationRequestMessage;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.SerializationUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;

class KafkaNotificationRecovererTest {

  @Test
  void shouldCaptureMalformedJsonAsDeserializerHeaderInsteadOfFailingBeforeListener() {
    final JsonDeserializer<NotificationRequestMessage> delegate =
        new JsonDeserializer<>(NotificationRequestMessage.class, new ObjectMapper());
    delegate.addTrustedPackages("com.jcaa.usersmanagement.infrastructure.messaging.kafka");
    final ErrorHandlingDeserializer<NotificationRequestMessage> deserializer =
        new ErrorHandlingDeserializer<>(delegate);
    deserializer.configure(Map.of(), false);
    final RecordHeaders headers = new RecordHeaders();

    final NotificationRequestMessage result =
        deserializer.deserialize(
            "user.notification.requested",
            headers,
            "{invalid-json".getBytes(StandardCharsets.UTF_8));

    org.assertj.core.api.Assertions.assertThat(result).isNull();
    org.assertj.core.api.Assertions.assertThat(
            headers.lastHeader(SerializationUtils.VALUE_DESERIALIZER_EXCEPTION_HEADER))
        .isNotNull();
  }

  @Test
  void shouldRouteMalformedRecordToDlqUsingOnlySafeSourceMetadata() {
    final KafkaMessagePublisher publisher = mock(KafkaMessagePublisher.class);
    final KafkaNotificationProperties properties =
        new KafkaNotificationProperties(
            true,
            "localhost:9092",
            "notify-service",
            "not-a-real-secret",
            "SASL_SSL",
            "SCRAM-SHA-256",
            "user.notification.requested",
            "user.notification.result",
            "user.notification.dlq",
            "users-api",
            "notify-service",
            1000);
    final ConsumerRecord<String, Object> record =
        new ConsumerRecord<>("user.notification.requested", 1, 27L, "record-key", null);
    final ConsumerRecordRecoverer recoverer =
        new KafkaMessagingConfiguration().notificationRequestRecoverer(publisher, properties);

    recoverer.accept(record, new IllegalArgumentException("sensitive payload details"));

    final org.mockito.ArgumentCaptor<MalformedNotificationDeadLetterMessage> deadLetterCaptor =
        org.mockito.ArgumentCaptor.forClass(MalformedNotificationDeadLetterMessage.class);
    verify(publisher)
        .publish(
            eq(properties.deadLetterTopic()),
            eq("user.notification.requested:27"),
            deadLetterCaptor.capture());
    org.assertj.core.api.Assertions.assertThat(deadLetterCaptor.getValue().sourcePartition())
        .isEqualTo(1);
    org.assertj.core.api.Assertions.assertThat(deadLetterCaptor.getValue().sourceOffset())
        .isEqualTo(27L);
    org.assertj.core.api.Assertions.assertThat(deadLetterCaptor.getValue().failureCode())
        .isEqualTo("MALFORMED_NOTIFICATION");
    org.assertj.core.api.Assertions.assertThat(deadLetterCaptor.getValue().toString())
        .doesNotContain("sensitive payload details", "record-key");
  }
}
