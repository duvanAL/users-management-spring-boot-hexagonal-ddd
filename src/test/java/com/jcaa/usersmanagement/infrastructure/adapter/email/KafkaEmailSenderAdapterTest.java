package com.jcaa.usersmanagement.infrastructure.adapter.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.jcaa.usersmanagement.domain.model.EmailDestinationModel;
import com.jcaa.usersmanagement.infrastructure.messaging.kafka.EmailOutboxRepository;
import com.jcaa.usersmanagement.infrastructure.messaging.kafka.NotificationRequestMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KafkaEmailSenderAdapterTest {

  @Mock private EmailOutboxRepository outboxRepository;

  @Test
  void shouldQueueEmailAndUseNotificationIdAsKafkaKey() {
    final KafkaEmailSenderAdapter adapter = new KafkaEmailSenderAdapter(outboxRepository);

    adapter.send(destination());

    final ArgumentCaptor<NotificationRequestMessage> messageCaptor =
        ArgumentCaptor.forClass(NotificationRequestMessage.class);
    verify(outboxRepository).enqueue(messageCaptor.capture());
    final NotificationRequestMessage message = messageCaptor.getValue();
    assertThat(message.schemaVersion()).isEqualTo(NotificationRequestMessage.CURRENT_SCHEMA_VERSION);
    assertThat(message.destinationEmail()).isEqualTo("ada@example.com");
    assertThat(message.subject()).isEqualTo("Account updated");
    assertThat(message.htmlContent()).isEqualTo("<p>Updated</p>");
  }

  private static EmailDestinationModel destination() {
    return new EmailDestinationModel(
        "ada@example.com", "Ada", "Account updated", "<p>Updated</p>");
  }
}
