package com.jcaa.usersmanagement.infrastructure.entrypoint.rest.controller;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.jcaa.usersmanagement.application.service.dto.command.CreateUserCommand;
import com.jcaa.usersmanagement.application.service.dto.command.UpdateUserCommand;
import com.jcaa.usersmanagement.application.service.dto.query.GetUserByIdQuery;
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
import com.jcaa.usersmanagement.infrastructure.security.JwtAuthenticationFilter;
import com.jcaa.usersmanagement.infrastructure.security.JwtTokenService;
import com.jcaa.usersmanagement.infrastructure.security.RestAccessDeniedHandler;
import com.jcaa.usersmanagement.infrastructure.security.RestAuthenticationEntryPoint;
import com.jcaa.usersmanagement.infrastructure.security.SecurityConfig;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = {UserRestController.class, HealthRestController.class},
    properties = {
      "spring.main.web-application-type=servlet",
      "app.cors.allowed-origins=https://users-management-api-docs.vercel.app"
    })
@Import({
  GlobalExceptionHandler.class,
  WebCorsConfig.class,
  SecurityConfig.class,
  JwtAuthenticationFilter.class,
  RestAuthenticationEntryPoint.class,
  RestAccessDeniedHandler.class
})
class UserRestControllerTest {

  private static final String USER_ID = "a291f5e0-1f17-4dca-b85b-9700a9e94273";
  private static final String HASH = "$2a$12$abcdefghijklmnopqrstabcdefghijklmnopqrstuvwxyzabcdefgh";
  private static final String SWAGGER_ORIGIN = "https://users-management-api-docs.vercel.app";

  @Autowired private MockMvc mockMvc;

  @MockBean private CreateUserUseCase createUserUseCase;
  @MockBean private UpdateUserUseCase updateUserUseCase;
  @MockBean private DeleteUserUseCase deleteUserUseCase;
  @MockBean private GetUserByIdUseCase getUserByIdUseCase;
  @MockBean private GetAllUsersUseCase getAllUsersUseCase;
  @MockBean private LoginUseCase loginUseCase;
  @MockBean private JwtTokenService jwtTokenService;

  @Test
  void shouldAllowPreflightFromSwagger() throws Exception {
    // Arrange, Act & Assert
    mockMvc.perform(options("/api/users")
            .header(HttpHeaders.ORIGIN, SWAGGER_ORIGIN)
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type,authorization"))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, SWAGGER_ORIGIN));
  }

  @Test
  void shouldKeepLegacyHealthEndpointPublic() throws Exception {
    // Arrange, Act & Assert
    mockMvc.perform(get("/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }

  @Test
  void shouldForceAnonymousRegistrationToMemberEvenWhenAdminRoleIsRequested() throws Exception {
    // Arrange
    when(createUserUseCase.execute(any())).thenReturn(user(UserRole.MEMBER));

    // Act & Assert
    mockMvc.perform(post("/api/users")
            .contentType("application/json")
            .content(createRequest("ADMIN")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.role").value("MEMBER"));

    final org.mockito.ArgumentCaptor<CreateUserCommand> command =
        org.mockito.ArgumentCaptor.forClass(CreateUserCommand.class);
    verify(createUserUseCase).execute(command.capture());
    assertThat(command.getValue().role()).isEqualTo("MEMBER");
  }

  @Test
  void shouldDefaultAnonymousRegistrationToMemberWhenRoleIsOmitted() throws Exception {
    // Arrange
    when(createUserUseCase.execute(any())).thenReturn(user(UserRole.MEMBER));

    // Act & Assert
    mockMvc.perform(post("/api/users")
            .contentType("application/json")
            .content("""
                {"id":"%s","name":"Ana Pérez","email":"ana@example.invalid",
                 "password":"Password987!"}
                """.formatted(USER_ID)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.role").value("MEMBER"));

    final org.mockito.ArgumentCaptor<CreateUserCommand> command =
        org.mockito.ArgumentCaptor.forClass(CreateUserCommand.class);
    verify(createUserUseCase).execute(command.capture());
    assertThat(command.getValue().role()).isEqualTo("MEMBER");
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void shouldAllowAdminToAssignRequestedRole() throws Exception {
    // Arrange
    when(createUserUseCase.execute(any())).thenReturn(user(UserRole.REVIEWER));

    // Act & Assert
    mockMvc.perform(post("/api/users")
            .contentType("application/json")
            .content(createRequest("REVIEWER")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.role").value("REVIEWER"));

    final org.mockito.ArgumentCaptor<CreateUserCommand> command =
        org.mockito.ArgumentCaptor.forClass(CreateUserCommand.class);
    verify(createUserUseCase).execute(command.capture());
    assertThat(command.getValue().role()).isEqualTo("REVIEWER");
  }

  @Test
  void shouldRejectInvalidCreateRequestBeforeCallingUseCase() throws Exception {
    // Arrange, Act & Assert
    mockMvc.perform(post("/api/users")
            .contentType("application/json")
            .content("""
                {"id":"","name":"A","email":"not-an-email",
                 "password":"short","role":"ADMIN"}
                """))
        .andExpect(status().isBadRequest());
    verify(createUserUseCase, never()).execute(any());
  }

  @Test
  void shouldRequireAuthenticationToListUsers() throws Exception {
    // Arrange, Act & Assert
    mockMvc.perform(get("/api/users"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401));
  }

  @Test
  @WithMockUser(roles = "REVIEWER")
  void shouldAllowReviewerToListUsers() throws Exception {
    // Arrange
    when(getAllUsersUseCase.execute()).thenReturn(List.of(user(UserRole.MEMBER)));

    // Act & Assert
    mockMvc.perform(get("/api/users"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(USER_ID));
  }

  @Test
  @WithMockUser(roles = "MEMBER")
  void shouldRejectMemberFromReadingUserById() throws Exception {
    // Arrange, Act & Assert
    mockMvc.perform(get("/api/users/{id}", USER_ID))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403));
    verify(getUserByIdUseCase, never()).execute(any(GetUserByIdQuery.class));
  }

  @Test
  @WithMockUser(roles = "REVIEWER")
  void shouldAllowReviewerToReadUserById() throws Exception {
    // Arrange
    when(getUserByIdUseCase.execute(any())).thenReturn(user(UserRole.MEMBER));

    // Act & Assert
    mockMvc.perform(get("/api/users/{id}", USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(USER_ID));
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void shouldUpdateUserUsingIdFromPath() throws Exception {
    // Arrange
    when(updateUserUseCase.execute(any(UpdateUserCommand.class))).thenReturn(user(UserRole.MEMBER));

    // Act & Assert
    mockMvc.perform(put("/api/users/{id}", USER_ID)
            .contentType("application/json")
            .content("""
                {"name":"Ana Pérez","email":"ana@example.invalid",
                 "password":"","role":"MEMBER","status":"ACTIVE"}
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(USER_ID));

    final org.mockito.ArgumentCaptor<UpdateUserCommand> command =
        org.mockito.ArgumentCaptor.forClass(UpdateUserCommand.class);
    verify(updateUserUseCase).execute(command.capture());
    assertThat(command.getValue().id()).isEqualTo(USER_ID);
  }

  @Test
  @WithMockUser(roles = "MEMBER")
  void shouldRejectMemberFromDeletingUser() throws Exception {
    // Arrange, Act & Assert
    mockMvc.perform(delete("/api/users/{id}", USER_ID))
        .andExpect(status().isForbidden());
    verify(deleteUserUseCase, never()).execute(any());
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void shouldAllowAdminToDeleteUser() throws Exception {
    // Arrange, Act & Assert
    mockMvc.perform(delete("/api/users/{id}", USER_ID))
        .andExpect(status().isNoContent());
    verify(deleteUserUseCase).execute(any());
  }

  @Test
  void shouldKeepLegacyLoginPublicAndReturnUnauthorizedForInvalidCredentials() throws Exception {
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
            .content(createRequest("ADMIN")))
        .andExpect(status().isConflict());
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void shouldHidePersistenceDetailsFromApiResponse() throws Exception {
    // Arrange
    when(getAllUsersUseCase.execute())
        .thenThrow(PersistenceException.becauseFindAllFailed(new SQLException("private detail")));

    // Act & Assert
    mockMvc.perform(get("/api/users"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.message").value("Error de persistencia."));
  }

  private static String createRequest(final String role) {
    return """
        {"id":"%s","name":"Ana Pérez","email":"ana@example.invalid",
         "password":"Password987!","role":"%s"}
        """.formatted(USER_ID, role);
  }

  private static UserModel user(final UserRole role) {
    return new UserModel(
        new UserId(USER_ID),
        new UserName("Ana Pérez"),
        new UserEmail("ana@example.invalid"),
        UserPassword.fromHash(HASH),
        role,
        UserStatus.ACTIVE);
  }
}
