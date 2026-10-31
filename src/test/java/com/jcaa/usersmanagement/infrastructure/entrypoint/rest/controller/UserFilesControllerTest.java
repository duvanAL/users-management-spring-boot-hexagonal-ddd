package com.jcaa.usersmanagement.infrastructure.entrypoint.rest.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jcaa.usersmanagement.application.port.in.ManageUserFilesUseCase;
import com.jcaa.usersmanagement.application.port.out.FileStoragePort;
import com.jcaa.usersmanagement.infrastructure.config.WebCorsConfig;
import com.jcaa.usersmanagement.infrastructure.security.JwtAuthenticationFilter;
import com.jcaa.usersmanagement.infrastructure.security.JwtTokenService;
import com.jcaa.usersmanagement.infrastructure.security.RestAccessDeniedHandler;
import com.jcaa.usersmanagement.infrastructure.security.RestAuthenticationEntryPoint;
import com.jcaa.usersmanagement.infrastructure.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = UserFilesController.class, properties = "spring.main.web-application-type=servlet")
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class,
    RestAccessDeniedHandler.class, WebCorsConfig.class})
class UserFilesControllerTest {
  @Autowired private MockMvc mvc;
  @MockBean private ManageUserFilesUseCase files;
  @MockBean private JwtTokenService tokens;

  @Test void anonymousCannotAccessContextOrFiles() throws Exception {
    mvc.perform(get("/api/users/owner/storage-context")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/users/owner/files")).andExpect(status().isUnauthorized());
  }

  @Test @WithMockUser(username = "owner", roles = "MEMBER")
  void memberCanReachDedicatedFileRoutes() throws Exception {
    when(files.execute(anyString(), anyBoolean(), any())).thenReturn(new FileStoragePort.Result(200, "{\"items\":[]}"));
    mvc.perform(get("/api/users/owner/files")).andExpect(status().isOk());
    mvc.perform(get("/api/users/owner/storage-context")).andExpect(status().isOk());
  }

  @Test @WithMockUser(username = "owner", roles = "MEMBER")
  void broadUserRoutesRemainRestricted() throws Exception {
    mvc.perform(get("/api/users")).andExpect(status().isForbidden());
    mvc.perform(get("/api/users/another")).andExpect(status().isForbidden());
  }
}
