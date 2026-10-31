package com.jcaa.usersmanagement.infrastructure.config;

import com.jcaa.usersmanagement.application.port.in.GetUserByIdUseCase;
import com.jcaa.usersmanagement.application.port.in.ManageUserFilesUseCase;
import com.jcaa.usersmanagement.application.port.out.FileStoragePort;
import com.jcaa.usersmanagement.application.service.ManageUserFilesService;
import com.jcaa.usersmanagement.infrastructure.adapter.storage.HttpFileStorageAdapter;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.HttpURLConnection;
import java.io.IOException;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class FileStorageConfiguration {
  @Bean
  FileStoragePort fileStoragePort(@Value("${APP_FILE_STORAGE_ENABLED:false}") final boolean enabled,
      @Value("${FILE_STORAGE_API_URL:}") final String url,
      @Value("${FILE_STORAGE_ALLOW_LOCAL_HTTP:false}") final boolean local,
      @Value("${FILE_STORAGE_TIMEOUT_MS:15000}") final int timeout,
      @Value("${FILE_STORAGE_MAX_SIZE_BYTES:10485760}") final long maxBytes,
      final ObjectProvider<HttpServletRequest> requests) {
    if (!enabled) return request -> new FileStoragePort.Result(503,
        "{\"status\":503,\"message\":\"File storage is not enabled.\"}");
    final URI uri = URI.create(url);
    if (uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null
        || uri.getFragment() != null || (!"https".equals(uri.getScheme())
        && !(local && "http".equals(uri.getScheme())))) {
      throw new IllegalStateException("FILE_STORAGE_API_URL must be a trusted HTTPS URL.");
    }
    if (timeout < 100 || timeout > 120000 || maxBytes < 1 || maxBytes > 104857600)
      throw new IllegalStateException("Invalid file storage limits.");
    final SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
      @Override
      protected void prepareConnection(final HttpURLConnection connection, final String method)
          throws IOException {
        super.prepareConnection(connection, method);
        connection.setInstanceFollowRedirects(false);
      }
    };
    factory.setConnectTimeout(Duration.ofMillis(timeout));
    factory.setReadTimeout(Duration.ofMillis(timeout));
    return new HttpFileStorageAdapter(RestClient.builder().baseUrl(url).requestFactory(factory).build(),
        () -> requests.getObject().getHeader(HttpHeaders.AUTHORIZATION), maxBytes);
  }

  @Bean
  ManageUserFilesUseCase manageUserFilesUseCase(final GetUserByIdUseCase users,
      final FileStoragePort files) { return new ManageUserFilesService(users, files); }
}
