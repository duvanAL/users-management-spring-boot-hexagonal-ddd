package com.jcaa.usersmanagement.infrastructure.in.web;

import com.jcaa.usersmanagement.application.ManageAvatarUseCase;
import org.junit.jupiter.api.Test;
import com.jcaa.usersmanagement.infrastructure.security.JwtTokenService;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Ajusta el import a tu ManageAvatarUseCase real
// import com.tuproyecto.application.port.in.ManageAvatarUseCase;

@WebMvcTest(AvatarController.class)
public class AvatarControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtTokenService jwtTokenService;

    @MockBean
    private ManageAvatarUseCase manageAvatarUseCase; // Simulamos el caso de uso

    // 1. Prueba de carga correcta
    @Test
    @WithMockUser(roles = "ADMIN") // Simulamos permiso ADMIN
    void uploadAvatar_Success() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", "dummy image content".getBytes());

        mockMvc.perform(multipart("/api/users/1/avatar")
                        .file(file)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Avatar actualizado exitosamente"));
    }

    // 2. Prueba de MIME inválido
    @Test
    @WithMockUser(roles = "ADMIN")
    void uploadAvatar_InvalidMimeType() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "document.pdf", "application/pdf", "dummy content".getBytes());

        mockMvc.perform(multipart("/api/users/1/avatar")
                        .file(file)
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    // 3. Prueba de tamaño inválido (> 2MB)
    @Test
    @WithMockUser(roles = "ADMIN")
    void uploadAvatar_ExceedsSizeLimit() throws Exception {
        // Creamos un arreglo de bytes que supere los 2MB (2097152 bytes)
        byte[] largeContent = new byte[2097153];
        MockMultipartFile file = new MockMultipartFile("file", "large.png", "image/png", largeContent);

        mockMvc.perform(multipart("/api/users/1/avatar")
                        .file(file)
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    // 4. Prueba de usuario inexistente (Devuelve 404)
    @Test
    @WithMockUser(roles = "ADMIN")
    void uploadAvatar_UserNotFound() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", "dummy image content".getBytes());

        // Configuramos el mock para que lance la excepción esperada
        Mockito.doThrow(new RuntimeException("User not found"))
                .when(manageAvatarUseCase).uploadAvatar(eq("999"), any(), any());

        mockMvc.perform(multipart("/api/users/999/avatar")
                        .file(file)
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    // 5. Prueba de permisos 403 (Rol incorrecto)
    @Test
    @WithMockUser(roles = "USER") // Un rol sin permisos
    void uploadAvatar_Forbidden() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", "dummy image content".getBytes());

        mockMvc.perform(multipart("/api/users/1/avatar")
                        .file(file)
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    // 6. Prueba de permisos 401 (Sin autenticación)
    @Test
    void uploadAvatar_Unauthorized() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", "dummy image content".getBytes());

        mockMvc.perform(multipart("/api/users/1/avatar")
                        .file(file)
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    // 7. Prueba de obtener URL firmada correctamente con rol REVIEWER
    @Test
    @WithMockUser(roles = "REVIEWER")
    void getAvatarUrl_Success() throws Exception {
        Mockito.when(manageAvatarUseCase.getAvatarUrl("1")).thenReturn("https://mock-url.supabase.co/signed-url");

        mockMvc.perform(get("/api/users/1/avatar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://mock-url.supabase.co/signed-url"));
    }
}