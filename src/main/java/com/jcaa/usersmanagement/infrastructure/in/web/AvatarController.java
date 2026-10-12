package com.jcaa.usersmanagement.infrastructure.in.web;

import com.jcaa.usersmanagement.application.ManageAvatarUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
// Asegúrate de importar tu ManageAvatarUseCase
// import com.tuproyecto.application.port.in.ManageAvatarUseCase;

@RestController
@RequestMapping("/api/users")
public class AvatarController {

    private final ManageAvatarUseCase manageAvatarUseCase;

    public AvatarController(ManageAvatarUseCase manageAvatarUseCase) {
        this.manageAvatarUseCase = manageAvatarUseCase;
    }

    // Endpoint PUT /api/users/{id}/avatar que recibe multipart/form-data
    @PutMapping("/{id}/avatar")
    @PreAuthorize("hasRole('ADMIN')") // Seguridad: Solo ADMIN
    public ResponseEntity<?> uploadAvatar(@PathVariable String id, @RequestParam("file") MultipartFile file) {
        try {
            // 1. Validar el tipo MIME: Solo acepta PNG o JPEG
            String contentType = file.getContentType();
            if (contentType == null || (!contentType.equals("image/png") && !contentType.equals("image/jpeg"))) {
                return ResponseEntity.badRequest().body("Tipo de archivo inválido. Solo se permite PNG o JPEG.");
            }

            // 2. Validar el tamaño: hasta 2 MB (2 * 1024 * 1024 bytes = 2097152 bytes)[cite: 1]
            if (file.getSize() > 2097152) {
                return ResponseEntity.badRequest().body("El archivo excede el límite de 2 MB.");
            }

            // 3. Si pasa las validaciones, enviamos al caso de uso
            manageAvatarUseCase.uploadAvatar(id, file.getBytes(), contentType);

            return ResponseEntity.ok().body(Map.of("message", "Avatar actualizado exitosamente"));

        } catch (RuntimeException e) {
            // Si el usuario no existe, devuelve 404
            if (e.getMessage().contains("User not found")) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.internalServerError().body("Error procesando el archivo: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // Endpoint GET /api/users/{id}/avatar
    @GetMapping("/{id}/avatar")
    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER')") // Seguridad: ADMIN y REVIEWER[cite: 1]
    public ResponseEntity<?> getAvatarUrl(@PathVariable String id) {
        try {
            // Llama al caso de uso para obtener el enlace firmado válido por 5 minutos[cite: 1]
            String signedUrl = manageAvatarUseCase.getAvatarUrl(id);
            return ResponseEntity.ok(Map.of("url", signedUrl));

        } catch (RuntimeException e) {
            // Responde 404 si el usuario no existe o no tiene foto en Supabase[cite: 1]
            return ResponseEntity.notFound().build();
        }
    }
}