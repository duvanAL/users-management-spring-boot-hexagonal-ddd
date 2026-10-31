package com.jcaa.usersmanagement.infrastructure.entrypoint.rest.controller;

import com.jcaa.usersmanagement.application.port.in.ManageUserFilesUseCase;
import com.jcaa.usersmanagement.application.port.out.FileStoragePort;
import com.jcaa.usersmanagement.application.service.ManageUserFilesService.FileAccessDeniedException;
import java.io.IOException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.beans.factory.annotation.Value;

@RestController
public final class UserFilesController {
  private final ManageUserFilesUseCase files;
  private final long maxBytes;
  public UserFilesController(final ManageUserFilesUseCase files,
      @Value("${FILE_STORAGE_MAX_SIZE_BYTES:10485760}") final long maxBytes) {
    this.files = files;
    this.maxBytes = maxBytes;
  }

  public record StorageContext(String id, boolean exists) {}

  @GetMapping("/api/users/{userId}/storage-context")
  public StorageContext context(@PathVariable final String userId, final Authentication auth) {
    files.requireUser(auth.getName(), admin(auth), userId);
    return new StorageContext(userId, true);
  }

  @GetMapping("/api/users/{userId}/files")
  public ResponseEntity<String> list(@PathVariable final String userId, final Authentication auth,
      @RequestParam(required = false) final Integer limit, @RequestParam(required = false) final String after) {
    return execute(auth, new FileStoragePort.Request(FileStoragePort.Operation.LIST, userId, null,
        null, null, null, null, null, limit, after));
  }

  @GetMapping("/api/users/{userId}/files/{fileId}")
  public ResponseEntity<String> get(@PathVariable final String userId, @PathVariable final String fileId,
      final Authentication auth) { return read(auth, userId, fileId, FileStoragePort.Operation.GET); }

  @GetMapping("/api/users/{userId}/files/{fileId}/download")
  public ResponseEntity<String> download(@PathVariable final String userId, @PathVariable final String fileId,
      final Authentication auth) { return read(auth, userId, fileId, FileStoragePort.Operation.DOWNLOAD); }

  @PostMapping(value = "/api/users/{userId}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<String> upload(@PathVariable final String userId, final Authentication auth,
      @RequestHeader("Idempotency-Key") final String key, @RequestParam("file") final MultipartFile file) throws IOException {
    return save(auth, userId, null, key, null, file);
  }

  @PutMapping(value = "/api/users/{userId}/files/{fileId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<String> replace(@PathVariable final String userId, @PathVariable final String fileId,
      final Authentication auth, @RequestHeader("Idempotency-Key") final String key,
      @RequestHeader(value = "If-Match", required = false) final String revision,
      @RequestParam("file") final MultipartFile file) throws IOException {
    return save(auth, userId, fileId, key, revision, file);
  }

  @DeleteMapping("/api/users/{userId}/files/{fileId}")
  public ResponseEntity<String> delete(@PathVariable final String userId, @PathVariable final String fileId,
      final Authentication auth, @RequestHeader("Idempotency-Key") final String key) {
    return execute(auth, new FileStoragePort.Request(FileStoragePort.Operation.DELETE, userId, fileId,
        key, null, null, null, null, null, null));
  }

  private ResponseEntity<String> read(final Authentication auth, final String userId, final String fileId,
      final FileStoragePort.Operation operation) {
    return execute(auth, new FileStoragePort.Request(operation, userId, fileId, null, null, null, null, null, null, null));
  }
  private ResponseEntity<String> save(final Authentication auth, final String userId, final String fileId,
      final String key, final String revision, final MultipartFile file) throws IOException {
    // Authorize before reading the upload into the application request.
    files.requireUser(auth.getName(), admin(auth), userId);
    if (file.getSize() > maxBytes) return ResponseEntity.status(413).contentType(MediaType.APPLICATION_JSON)
        .body("{\"status\":413,\"message\":\"File exceeds the configured limit.\"}");
    if (file.getOriginalFilename() == null || file.getContentType() == null)
      return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON)
          .body("{\"status\":400,\"message\":\"File name and content type are required.\"}");
    return execute(auth, new FileStoragePort.Request(fileId == null ? FileStoragePort.Operation.UPLOAD : FileStoragePort.Operation.REPLACE,
        userId, fileId, key, revision, file.getOriginalFilename(), file.getContentType(), file.getBytes(), null, null));
  }
  private ResponseEntity<String> execute(final Authentication auth, final FileStoragePort.Request request) {
    final FileStoragePort.Result result = files.execute(auth.getName(), admin(auth), request);
    return ResponseEntity.status(result.status()).contentType(MediaType.APPLICATION_JSON)
        .header("Cache-Control", "no-store").body(result.json());
  }
  private static boolean admin(final Authentication auth) {
    return auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
  }
  @ExceptionHandler(FileAccessDeniedException.class)
  ResponseEntity<String> denied() {
    return ResponseEntity.status(403).contentType(MediaType.APPLICATION_JSON)
        .body("{\"status\":403,\"message\":\"Forbidden file operation.\"}");
  }

  @ExceptionHandler({MissingRequestHeaderException.class, MissingServletRequestPartException.class,
      MethodArgumentTypeMismatchException.class})
  ResponseEntity<String> invalidRequest() {
    return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON)
        .body("{\"status\":400,\"message\":\"Invalid file request or required header is missing.\"}");
  }
}
