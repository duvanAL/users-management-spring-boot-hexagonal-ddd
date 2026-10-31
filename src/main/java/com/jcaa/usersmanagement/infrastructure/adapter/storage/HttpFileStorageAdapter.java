package com.jcaa.usersmanagement.infrastructure.adapter.storage;

import com.jcaa.usersmanagement.application.port.out.FileStoragePort;
import java.util.function.Supplier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public final class HttpFileStorageAdapter implements FileStoragePort {
  private final RestClient client;
  private final Supplier<String> authorization;
  private final long maxBytes;

  public HttpFileStorageAdapter(final RestClient client, final Supplier<String> authorization,
      final long maxBytes) {
    this.client = client;
    this.authorization = authorization;
    this.maxBytes = maxBytes;
  }

  @Override
  public Result execute(final Request command) {
    if (command.content() != null && command.content().length > maxBytes) return error(413);
    final HttpMethod method = switch (command.operation()) {
      case UPLOAD -> HttpMethod.POST;
      case REPLACE -> HttpMethod.PUT;
      case DELETE -> HttpMethod.DELETE;
      default -> HttpMethod.GET;
    };
    try {
      final RestClient.RequestBodySpec request = client.method(method).uri(builder -> {
        builder.pathSegment("api", "users", command.userId(), "files");
        if (command.fileId() != null) builder.pathSegment(command.fileId());
        if (command.operation() == Operation.DOWNLOAD) builder.pathSegment("download");
        if (command.limit() != null) builder.queryParam("limit", command.limit());
        if (command.after() != null) builder.queryParam("after", command.after());
        return builder.build();
      }).header(HttpHeaders.AUTHORIZATION, authorization.get()).accept(MediaType.APPLICATION_JSON);
      if (command.idempotencyKey() != null) request.header("Idempotency-Key", command.idempotencyKey());
      if (command.ifMatch() != null) request.header(HttpHeaders.IF_MATCH, command.ifMatch());
      if (command.content() != null) {
        final MultipartBodyBuilder multipart = new MultipartBodyBuilder();
        multipart.part("file", new ByteArrayResource(command.content()))
            .filename(command.fileName()).contentType(MediaType.parseMediaType(command.contentType()));
        request.contentType(MediaType.MULTIPART_FORM_DATA).body(multipart.build());
      }
      return request.exchange((sent, response) -> {
        final int status = response.getStatusCode().value();
        if (status < 200 || (status >= 300 && status < 400)) return error(502);
        if (status >= 400) return error(status);
        // A download response is JSON with a short-lived URL, never a binary buffer.
        final byte[] body = response.getBody().readNBytes(1024 * 1024 + 1);
        if (body.length > 1024 * 1024) return error(502);
        return new Result(status, new String(body, java.nio.charset.StandardCharsets.UTF_8));
      });
    } catch (final RestClientException | IllegalArgumentException exception) {
      return error(503); // No provider body, token, URL or credential enters logs/errors.
    }
  }

  private static Result error(final int status) {
    return new Result(status, "{\"status\":" + status + ",\"message\":\"File operation could not be completed.\"}");
  }
}
