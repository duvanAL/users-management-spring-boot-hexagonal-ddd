package com.jcaa.usersmanagement.infrastructure.adapter.email;

import com.jcaa.usersmanagement.application.port.out.EmailSenderPort;
import com.jcaa.usersmanagement.domain.model.EmailDestinationModel;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Defers direct provider I/O until after commit and runs it outside the request transaction. */
@Slf4j
public final class AfterCommitEmailSenderAdapter implements EmailSenderPort {

  private final EmailSenderPort delegate;
  private final Executor executor;

  public AfterCommitEmailSenderAdapter(final EmailSenderPort delegate, final Executor executor) {
    this.delegate = delegate;
    this.executor = executor;
  }

  @Override
  public void send(final EmailDestinationModel destination) {
    if (TransactionSynchronizationManager.isActualTransactionActive()
        && TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              enqueue(destination);
            }
          });
      return;
    }
    enqueue(destination);
  }

  @Override
  public void send(final EmailDestinationModel destination, final String idempotencyKey) {
    if (TransactionSynchronizationManager.isActualTransactionActive()
        && TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              enqueue(destination, idempotencyKey);
            }
          });
      return;
    }
    enqueue(destination, idempotencyKey);
  }

  private void enqueue(final EmailDestinationModel destination) {
    enqueue(destination, null);
  }

  private void enqueue(final EmailDestinationModel destination, final String idempotencyKey) {
    try {
      executor.execute(
          () -> {
            try {
              if (idempotencyKey == null) {
                delegate.send(destination);
              } else {
                delegate.send(destination, idempotencyKey);
              }
            } catch (final RuntimeException exception) {
              log.error(
                  "Direct email delivery failed after transaction commit. type={}",
                  exception.getClass().getSimpleName());
            }
          });
    } catch (final RuntimeException exception) {
      // The transaction has already committed; do not turn a successful write into an HTTP 500.
      log.error(
          "Direct email delivery could not be queued after transaction commit. type={}",
          exception.getClass().getSimpleName());
    }
  }
}
