package com.jcaa.usersmanagement.application.service;

import com.jcaa.usersmanagement.application.port.in.DeleteUserUseCase;
import com.jcaa.usersmanagement.application.port.out.DeleteUserPort;
import com.jcaa.usersmanagement.application.port.out.GetUserByIdPort;
import com.jcaa.usersmanagement.application.port.out.DomainEventPublisherPort;
import com.jcaa.events.DomainEvent;
import com.jcaa.usersmanagement.application.service.dto.command.DeleteUserCommand;
import com.jcaa.usersmanagement.application.service.mapper.UserApplicationMapper;
import com.jcaa.usersmanagement.domain.exception.UserNotFoundException;
import com.jcaa.usersmanagement.domain.valueobject.UserId;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.Map;
import java.util.Optional;

@Service
public class DeleteUserService implements DeleteUserUseCase {

  private final DeleteUserPort deleteUserPort;
  private final GetUserByIdPort getUserByIdPort;
  private final Validator validator;
  private final Optional<DomainEventPublisherPort> domainEvents;

  public DeleteUserService(DeleteUserPort deleteUserPort, GetUserByIdPort getUserByIdPort,
      Validator validator) {
    this(deleteUserPort, getUserByIdPort, validator, Optional.empty());
  }

  @Autowired
  public DeleteUserService(DeleteUserPort deleteUserPort, GetUserByIdPort getUserByIdPort,
      Validator validator, Optional<DomainEventPublisherPort> domainEvents) {
    this.deleteUserPort = deleteUserPort;
    this.getUserByIdPort = getUserByIdPort;
    this.validator = validator;
    this.domainEvents = domainEvents;
  }

  @Override
  @Transactional
  public void execute(final DeleteUserCommand command) {
    validateCommand(command);

    final UserId userId = UserApplicationMapper.fromDeleteCommandToUserId(command);
    ensureUserExists(userId);
    deleteUserPort.delete(userId);
    domainEvents.ifPresent(events -> events.enqueue(
        DomainEvent.create("user.deleted", Map.of("userId", userId.value()))));
  }

  private void validateCommand(final DeleteUserCommand command) {
    final Set<ConstraintViolation<DeleteUserCommand>> violations = validator.validate(command);
    if (!violations.isEmpty()) {
      throw new ConstraintViolationException(violations);
    }
  }

  private void ensureUserExists(final UserId userId) {
    getUserByIdPort
        .getById(userId)
        .orElseThrow(() -> UserNotFoundException.becauseIdWasNotFound(userId.value()));
  }
}
