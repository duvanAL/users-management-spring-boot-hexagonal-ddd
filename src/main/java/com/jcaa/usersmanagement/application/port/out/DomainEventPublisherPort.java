package com.jcaa.usersmanagement.application.port.out;

import com.jcaa.events.DomainEvent;

/** Enqueues an event in the same database transaction as the user mutation. */
public interface DomainEventPublisherPort {
  void enqueue(DomainEvent event);
}
