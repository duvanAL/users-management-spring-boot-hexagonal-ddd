package com.jcaa.usersmanagement.infrastructure.in.web;

import com.jcaa.usersmanagement.application.ManageAvatarUseCase;
import com.jcaa.usersmanagement.domain.exception.UserNotFoundException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/users")
public class AvatarController {

    private static final long MAX_FILE_SIZE_BYTES = 2L * 1024 * 1024;
    private static final int MAX_IMAGE_DIMENSION = 4096;
    private static final long MAX_IMAGE_PIXELS = 4_194_304L;

    private final ObjectProvider<ManageAvatarUseCase> manageAvatarUseCaseProvider;

    public AvatarController(final ObjectProvider<ManageAvatarUseCase> manageAvatarUseCaseProvider) {
        this.manageAvatarUseCaseProvider = manageAvatarUseCaseProvider;
    }

    @PutMapping("/{id}/avatar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> uploadAvatar(
            @PathVariable final String id, @RequestParam("file") final MultipartFile file) {
        final String contentType = file.getContentType();
        if (!isSupportedContentType(contentType)) {
            return ResponseEntity.badRequest().body("Tipo de archivo inválido. Solo se permite PNG o JPEG.");
        }
        if (file.isEmpty() || file.getSize() > MAX_FILE_SIZE_BYTES) {
            return ResponseEntity.badRequest().body("El archivo debe ser una imagen de hasta 2 MB.");
        }

        try {
            final byte[] bytes = file.getBytes();
            if (!isValidImage(bytes, contentType)) {
                return ResponseEntity.badRequest().body("El contenido no es una imagen PNG o JPEG válida.");
            }
            final ManageAvatarUseCase useCase = manageAvatarUseCaseProvider.getIfAvailable();
            if (useCase == null) {
                return ResponseEntity.status(503).body("Supabase Storage no está habilitado.");
            }
            useCase.uploadAvatar(id, bytes, contentType);
            return ResponseEntity.ok(Map.of("message", "Avatar actualizado exitosamente"));
        } catch (UserNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (RuntimeException e) {
            return ResponseEntity.status(502).body("No se pudo guardar el avatar en el almacenamiento externo.");
        } catch (IOException e) {
            return ResponseEntity.badRequest().body("No se pudo leer el archivo enviado.");
        }
    }

    @GetMapping("/{id}/avatar")
    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER')")
    public ResponseEntity<?> getAvatarUrl(@PathVariable final String id) {
        try {
            final ManageAvatarUseCase useCase = manageAvatarUseCaseProvider.getIfAvailable();
            if (useCase == null) {
                return ResponseEntity.status(503).body("Supabase Storage no está habilitado.");
            }
            return ResponseEntity.ok(Map.of("url", useCase.getAvatarUrl(id)));
        } catch (UserNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (RuntimeException e) {
            return ResponseEntity.status(502).body("No se pudo consultar el avatar en el almacenamiento externo.");
        }
    }

    private boolean isSupportedContentType(final String contentType) {
        return MediaType.IMAGE_PNG_VALUE.equalsIgnoreCase(contentType)
                || MediaType.IMAGE_JPEG_VALUE.equalsIgnoreCase(contentType);
    }

    private boolean isValidImage(final byte[] bytes, final String contentType) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) {
                return false;
            }
            final Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return false;
            }
            final ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                final String format = reader.getFormatName();
                final boolean formatMatches = MediaType.IMAGE_PNG_VALUE.equalsIgnoreCase(contentType)
                        ? "png".equalsIgnoreCase(format)
                        : "jpeg".equalsIgnoreCase(format) || "jpg".equalsIgnoreCase(format);
                final int width = reader.getWidth(0);
                final int height = reader.getHeight(0);
                if (!formatMatches || width <= 0 || height <= 0
                        || width > MAX_IMAGE_DIMENSION || height > MAX_IMAGE_DIMENSION
                        || (long) width * height > MAX_IMAGE_PIXELS) {
                    return false;
                }
                final BufferedImage decoded = reader.read(0);
                return decoded != null;
            } finally {
                reader.dispose();
            }
        }
    }
}
