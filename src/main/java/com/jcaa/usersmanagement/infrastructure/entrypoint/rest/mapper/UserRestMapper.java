package com.jcaa.usersmanagement.infrastructure.entrypoint.rest.mapper;

import com.jcaa.usersmanagement.application.service.dto.command.CreateUserCommand;
import com.jcaa.usersmanagement.application.service.dto.command.DeleteUserCommand;
import com.jcaa.usersmanagement.application.service.dto.command.UpdateUserCommand;
import com.jcaa.usersmanagement.application.service.dto.query.GetUserByIdQuery;
import com.jcaa.usersmanagement.domain.model.UserModel;
import com.jcaa.usersmanagement.infrastructure.entrypoint.rest.dto.request.CreateUserRestRequest;
import com.jcaa.usersmanagement.infrastructure.entrypoint.rest.dto.request.UpdateUserRestRequest;
import com.jcaa.usersmanagement.infrastructure.entrypoint.rest.dto.response.UserRestResponse;
import lombok.experimental.UtilityClass;

import java.util.List;

@UtilityClass
public class UserRestMapper {

  public CreateUserCommand toCreateCommand(
      final CreateUserRestRequest request, final String assignedRole) {
    return new CreateUserCommand(
        request.id(),
        request.name(),
        request.email(),
        request.password(),
        assignedRole);
  }

  public UpdateUserCommand toUpdateCommand(final String id, final UpdateUserRestRequest request) {
    return new UpdateUserCommand(
        id,
        request.name(),
        request.email(),
        request.password(),
        request.role(),
        request.status());
  }

  public GetUserByIdQuery toGetByIdQuery(final String id) {
    return new GetUserByIdQuery(id);
  }

  public DeleteUserCommand toDeleteCommand(final String id) {
    return new DeleteUserCommand(id);
  }

  public UserRestResponse toResponse(final UserModel user) {
    return new UserRestResponse(
        user.getId().value(),
        user.getName().value(),
        user.getEmail().value(),
        user.getRole().name(),
        user.getStatus().name());
  }

  public List<UserRestResponse> toResponseList(final List<UserModel> users) {
    return users.stream().map(UserRestMapper::toResponse).toList();
  }
}

