package com.jcaa.usersmanagement.infrastructure.entrypoint.rest.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jcaa.usersmanagement.application.port.in.CreateUserUseCase;
import com.jcaa.usersmanagement.application.port.in.DeleteUserUseCase;
import com.jcaa.usersmanagement.application.port.in.GetAllUsersUseCase;
import com.jcaa.usersmanagement.application.port.in.GetUserByIdUseCase;
import com.jcaa.usersmanagement.application.port.in.LoginUseCase;
import com.jcaa.usersmanagement.application.port.in.UpdateUserUseCase;
import com.jcaa.usersmanagement.application.service.dto.command.UpdateUserCommand;
import com.jcaa.usersmanagement.domain.enums.UserRole;
import com.jcaa.usersmanagement.domain.enums.UserStatus;
import com.jcaa.usersmanagement.domain.exception.InvalidCredentialsException;
import com.jcaa.usersmanagement.domain.exception.UserAlreadyExistsException;
import com.jcaa.usersmanagement.domain.exception.UserNotFoundException;
import com.jcaa.usersmanagement.domain.model.UserModel;
import com.jcaa.usersmanagement.domain.valueobject.UserEmail;
import com.jcaa.usersmanagement.domain.valueobject.UserId;
import com.jcaa.usersmanagement.domain.valueobject.UserName;
import com.jcaa.usersmanagement.domain.valueobject.UserPassword;
import com.jcaa.usersmanagement.infrastructure.adapter.persistence.exception.PersistenceException;
import com.jcaa.usersmanagement.infrastructure.config.WebCorsConfig;
import com.jcaa.usersmanagement.infrastructure.entrypoint.rest.advice.GlobalExceptionHandler;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = UserRestController.class,
    properties = {
        "spring.main.web-application-type=servlet",
        "app.cors.allowed-origins=https://users-management-api-docs.vercel.app"
    })
@Import({GlobalExceptionHandler.class, WebCorsConfig.class})
class UserRestControllerTest {

  private static final String USER_ID = "a291f5e0-1f17-4dca-b85b-9700a9e94273";
  private static final String HASH = "$2a$12$abcdefghijklmnopqrstabcdefghijklmnopqrstuvwxyzabcdefgh";

  @Autowired private MockMvc mockMvc;

  @MockBean private CreateUserUseCase createUserUseCase;
  @MockBean private UpdateUserUseCase updateUserUseCase;
  @MockBean private DeleteUserUseCase deleteUserUseCase;
  @MockBean private GetUserByIdUseCase getUserByIdUseCase;
  @MockBean private GetAllUsersUseCase getAllUsersUseCase;
  @MockBean private LoginUseCase loginUseCase;

  @Test
  void shouldAllowPreflightRequestsFromTheConfiguredSwaggerSite() throws Exception {
    // Arrange, Act & Assert
    mockMvc.perform(options("/api/users")
            .header(HttpHeaders.ORIGIN, "https://users-management-api-docs.vercel.app")
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
        .andExpect(status().isOk())
        .andExpect(header()
            .string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://users-management-api-docs.vercel.app"));
  }

  @Test
  void shouldCreateUserAndReturnCreatedResponseWithoutPassword() throws Exception {
    // Arrange
    when(createUserUseCase.execute(any())).thenReturn(user());

    // Act & Assert
    mockMvc.perform(post("/api/users")
            .contentType("application/json")
            .content("""
                {"id":"%s","name":"Ana Pérez","email":"ana@example.invalid",
                 "password":"Password987!","role":"MEMBER"}
                """.formatted(USER_ID)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(USER_ID))
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.password").doesNotExist());
  }

  @Test
  void shouldRejectInvalidCreateRequestBeforeCallingUseCase() throws Exception {
    // Arrange, Act & Assert
    mockMvc.perform(post("/api/users")
            .contentType("application/json")
            .content("""
                {"id":"","name":"A","email":"not-an-email",
                 "password":"short","role":"MEMBER"}
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.message").isNotEmpty());

    verify(createUserUseCase, never()).execute(any());
  }

  @Test
  void shouldReturnUsersFromListEndpoint() throws Exception {
    // Arrange
    when(getAllUsersUseCase.execute()).thenReturn(java.util.List.of(user()));

    // Act & Assert
    mockMvc.perform(get("/api/users"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(USER_ID))
        .andExpect(jsonPath("$[0].email").value("ana@example.invalid"));
  }

  @Test
  void shouldReturnNotFoundForUnknownUser() throws Exception {
    // Arrange
    when(getUserByIdUseCase.execute(any()))
        .thenThrow(UserNotFoundException.becauseIdWasNotFound(USER_ID));

    // Act & Assert
    mockMvc.perform(get("/api/users/{id}", USER_ID))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.message").isNotEmpty());
  }

  @Test
  void shouldUpdateUserUsingIdFromPath() throws Exception {
    // Arrange
    when(updateUserUseCase.execute(any(UpdateUserCommand.class))).thenReturn(user());

    // Act & Assert
    mockMvc.perform(put("/api/users/{id}", USER_ID)
            .contentType("application/json")
            .content("""
                {"name":"Ana Pérez","email":"ana@example.invalid",
                 "password":"","role":"MEMBER","status":"ACTIVE"}
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(USER_ID));

    org.mockito.ArgumentCaptor<UpdateUserCommand> command =
        org.mockito.ArgumentCaptor.forClass(UpdateUserCommand.class);
    verify(updateUserUseCase).execute(command.capture());
    org.assertj.core.api.Assertions.assertThat(command.getValue().id()).isEqualTo(USER_ID);
  }

  @Test
  void shouldDeleteUserAndReturnNoContent() throws Exception {
    // Arrange, Act & Assert
    mockMvc.perform(delete("/api/users/{id}", USER_ID))
        .andExpect(status().isNoContent());

    verify(deleteUserUseCase).execute(any());
  }

  @Test
  void shouldReturnUnauthorizedForInvalidLogin() throws Exception {
    // Arrange
    when(loginUseCase.execute(any()))
        .thenThrow(InvalidCredentialsException.becauseCredentialsAreInvalid());

    // Act & Assert
    mockMvc.perform(post("/api/users/login")
            .contentType("application/json")
            .content("""
                {"email":"ana@example.invalid","password":"WrongPass987!"}
                """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401));
  }

  @Test
  void shouldReturnConflictForDuplicateEmail() throws Exception {
    // Arrange
    when(createUserUseCase.execute(any()))
        .thenThrow(UserAlreadyExistsException.becauseEmailAlreadyExists("ana@example.invalid"));

    // Act & Assert
    mockMvc.perform(post("/api/users")
            .contentType("application/json")
            .content("""
                {"id":"%s","name":"Ana Pérez","email":"ana@example.invalid",
                 "password":"Password987!","role":"MEMBER"}
                """.formatted(USER_ID)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409));
  }

  @Test
  void shouldHidePersistenceDetailsFromApiResponse() throws Exception {
    // Arrange
    when(getAllUsersUseCase.execute())
        .thenThrow(PersistenceException.becauseFindAllFailed(new SQLException("private detail")));

    // Act & Assert
    mockMvc.perform(get("/api/users"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.status").value(500))
        .andExpect(jsonPath("$.message").value("Error de persistencia."));
  }

  @Test
  void shouldReturnSuccessfulLoginResponse() throws Exception {
    // Arrange
    when(loginUseCase.execute(any())).thenReturn(user());

    // Act & Assert
    mockMvc.perform(post("/api/users/login")
            .contentType("application/json")
            .content("""
                {"email":"ana@example.invalid","password":"Password987!"}
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(USER_ID))
        .andExpect(jsonPath("$.password").doesNotExist());
  }

  private static UserModel user() {
    return new UserModel(
        new UserId(USER_ID),
        new UserName("Ana Pérez"),
        new UserEmail("ana@example.invalid"),
        UserPassword.fromHash(HASH),
        UserRole.MEMBER,
        UserStatus.ACTIVE);
  }
}
