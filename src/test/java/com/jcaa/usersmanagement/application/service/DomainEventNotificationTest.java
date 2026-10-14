package com.jcaa.usersmanagement.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jcaa.events.DomainEvent;
import com.jcaa.usersmanagement.application.port.out.DomainEventPublisherPort;
import com.jcaa.usersmanagement.application.port.out.EmailSenderPort;
import com.jcaa.usersmanagement.domain.enums.UserRole;
import com.jcaa.usersmanagement.domain.enums.UserStatus;
import com.jcaa.usersmanagement.domain.model.UserModel;
import com.jcaa.usersmanagement.domain.valueobject.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DomainEventNotificationTest {
  @Test
  void publishesUserEventsWithoutSendingDuplicateLegacyEmails() {
    EmailSenderPort email = mock(EmailSenderPort.class);
    DomainEventPublisherPort events = mock(DomainEventPublisherPort.class);
    EmailNotificationService service = new EmailNotificationService(email, Optional.of(events));
    UserModel user =
        new UserModel(
            new UserId("usr-1"),
            new UserName("Demo"),
            new UserEmail("demo@example.test"),
            UserPassword.fromPlainText("Demo-password-123!"),
            UserRole.MEMBER,
            UserStatus.ACTIVE);
    service.notifyUserCreated(user);
    service.notifyUserUpdated(user);
    ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
    verify(events, times(2)).enqueue(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(DomainEvent::eventName)
        .containsExactly("user.created", "user.updated");
    assertThat(captor.getAllValues().get(0).payload()).containsEntry("userId", "usr-1");
    assertThat(captor.getAllValues().get(0).payload()).doesNotContainKeys("password", "token");
    verifyNoInteractions(email);
  }
}
