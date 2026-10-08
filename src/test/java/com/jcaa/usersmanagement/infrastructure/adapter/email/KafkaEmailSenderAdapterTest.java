package com.jcaa.usersmanagement.infrastructure.adapter.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.jcaa.usersmanagement.domain.exception.EmailSenderException;
import com.jcaa.usersmanagement.domain.model.EmailDestinationModel;
import com.jcaa.usersmanagement.infrastructure.messaging.kafka.KafkaMessagePublisher;
import com.jcaa.usersmanagement.infrastructure.messaging.kafka.KafkaNotificationProperties;
import com.jcaa.usersmanagement.infrastructure.messaging.kafka.NotificationRequestMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KafkaEmailSenderAdapterTest {

  @Mock private KafkaMessagePublisher publisher;

  @Test
  void shouldQueueEmailAndUseNotificationIdAsKafkaKey() {
    final KafkaEmailSenderAdapter adapter = new KafkaEmailSenderAdapter(publisher, properties());

    adapter.send(destination());

    final ArgumentCaptor<NotificationRequestMessage> messageCaptor =
        ArgumentCaptor.forClass(NotificationRequestMessage.class);
    verify(publisher)
        .publish(eq("user.notification.requested"), any(String.class), messageCaptor.capture());
    final NotificationRequestMessage message = messageCaptor.getValue();
    assertThat(message.schemaVersion()).isEqualTo(NotificationRequestMessage.CURRENT_SCHEMA_VERSION);
    assertThat(message.destinationEmail()).isEqualTo("ada@example.com");
    assertThat(message.subject()).isEqualTo("Account updated");
    assertThat(message.htmlContent()).isEqualTo("<p>Updated</p>");
    verify(publisher)
        .publish(
            "user.notification.requested", message.notificationId().toString(), message);
  }

  @Test
  void shouldHideKafkaFailureDetailsFromEmailException() {
    doThrow(new IllegalStateException("broker secret detail"))
        .when(publisher)
        .publish(eq("user.notification.requested"), any(String.class), any());
    final KafkaEmailSenderAdapter adapter = new KafkaEmailSenderAdapter(publisher, properties());

    assertThatThrownBy(() -> adapter.send(destination()))
        .isInstanceOf(EmailSenderException.class)
        .hasMessage("La notificación por correo no pudo ser enviada.")
        .hasMessageNotContaining("broker secret detail");
  }

  private static KafkaNotificationProperties properties() {
    return new KafkaNotificationProperties(
        true,
        "localhost:9092",
        "users-api",
        "fake-password",
        "SASL_SSL",
        "SCRAM-SHA-256",
        "user.notification.requested",
        "user.notification.result",
        "user.notification.dlq",
        "users-api",
        "notify-service",
        1000);
  }

  private static EmailDestinationModel destination() {
    return new EmailDestinationModel(
        "ada@example.com", "Ada", "Account updated", "<p>Updated</p>");
  }
}
