package com.jcaa.usersmanagement.infrastructure.entrypoint.rest.controller;

import com.jcaa.usersmanagement.application.port.in.GetUserByIdUseCase;
import com.jcaa.usersmanagement.application.service.dto.query.GetUserByIdQuery;
import com.jcaa.usersmanagement.infrastructure.entrypoint.rest.dto.response.UserRestResponse;
import com.jcaa.usersmanagement.infrastructure.entrypoint.rest.mapper.UserRestMapper;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProfileRestController {
  private final GetUserByIdUseCase users;
  public ProfileRestController(GetUserByIdUseCase users) { this.users = users; }
  @GetMapping("/api/me")
  public UserRestResponse profile(Authentication authentication) {
    return UserRestMapper.toResponse(users.execute(new GetUserByIdQuery(authentication.getName())));
  }
}
