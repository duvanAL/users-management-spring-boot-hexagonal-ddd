package com.jcaa.usersmanagement.infrastructure.adapter.email;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.jcaa.usersmanagement.application.port.out.EmailSenderPort;
import com.jcaa.usersmanagement.domain.model.EmailDestinationModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

class AfterCommitEmailSenderAdapterTest {

  private final EmailSenderPort delegate = mock(EmailSenderPort.class);
  private final AfterCommitEmailSenderAdapter adapter =
      new AfterCommitEmailSenderAdapter(delegate, Runnable::run);
  private final EmailDestinationModel destination =
      new EmailDestinationModel("ada@example.com", "Ada", "Subject", "<p>Body</p>");

  @AfterEach
  void clearTransactionSynchronization() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
    TransactionSynchronizationManager.setActualTransactionActive(false);
  }

  @Test
  void shouldRunProviderCallOnlyAfterTransactionCommit() {
    TransactionSynchronizationManager.initSynchronization();
    TransactionSynchronizationManager.setActualTransactionActive(true);

    adapter.send(destination);

    verify(delegate, never()).send(destination);
    TransactionSynchronizationUtils.triggerAfterCommit();
    verify(delegate).send(destination);
  }

  @Test
  void shouldNotSendEmailWhenTransactionRollsBack() {
    TransactionSynchronizationManager.initSynchronization();
    TransactionSynchronizationManager.setActualTransactionActive(true);

    adapter.send(destination);

    TransactionSynchronizationManager.clearSynchronization();
    verify(delegate, never()).send(destination);
  }
}
