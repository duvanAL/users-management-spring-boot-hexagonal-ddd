package com.jcaa.usersmanagement.infrastructure.entrypoint.rest.controller;

import com.jcaa.usersmanagement.application.port.in.LoginUseCase;
import com.jcaa.usersmanagement.application.service.dto.command.LoginCommand;
import com.jcaa.usersmanagement.domain.model.UserModel;
import com.jcaa.usersmanagement.infrastructure.entrypoint.rest.dto.request.LoginRestRequest;
import com.jcaa.usersmanagement.infrastructure.entrypoint.rest.dto.response.LoginRestResponse;
import com.jcaa.usersmanagement.infrastructure.security.JwtTokenService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthRestController {

  private static final String BEARER_TOKEN_TYPE = "Bearer";

  private final LoginUseCase loginUseCase;
  private final JwtTokenService jwtTokenService;

  @PostMapping("/login")
  @Operation(summary = "Autenticar usuario y obtener un token JWT")
  public LoginRestResponse login(@Valid @RequestBody final LoginRestRequest request) {
    final UserModel user = loginUseCase.execute(new LoginCommand(request.email(), request.password()));
    return new LoginRestResponse(
        jwtTokenService.generate(user), BEARER_TOKEN_TYPE, jwtTokenService.expirationSeconds());
  }
}
