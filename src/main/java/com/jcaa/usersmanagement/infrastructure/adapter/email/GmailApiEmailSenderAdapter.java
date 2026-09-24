package com.jcaa.usersmanagement.infrastructure.adapter.email;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcaa.usersmanagement.application.port.out.EmailSenderPort;
import com.jcaa.usersmanagement.domain.exception.EmailSenderException;
import com.jcaa.usersmanagement.domain.model.EmailDestinationModel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Properties;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;

/** Sends mail from the configured Google mailbox through Gmail's REST API (no SMTP). */
@Slf4j
public final class GmailApiEmailSenderAdapter implements EmailSenderPort {

  private static final URI OAUTH_TOKEN_URI = URI.create("https://oauth2.googleapis.com/token");
  private static final URI GMAIL_SEND_URI =
      URI.create("https://gmail.googleapis.com/gmail/v1/users/me/messages/send");
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
  private static final String CONTENT_TYPE_HTML = "text/html; charset=UTF-8";
  private static final String LOG_SENT = "[GmailApiEmailSenderAdapter] correo enviado exitosamente.";

  private final GmailApiConfig config;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  public GmailApiEmailSenderAdapter(final GmailApiConfig config) {
    this(config, HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build(), new ObjectMapper());
  }

  GmailApiEmailSenderAdapter(
      final GmailApiConfig config, final HttpClient httpClient, final ObjectMapper objectMapper) {
    this.config = config;
    this.httpClient = httpClient;
    this.objectMapper = objectMapper;
    validateConfig();
  }

  @Override
  public void send(final EmailDestinationModel destination) {
    try {
      final String accessToken = refreshAccessToken();
      final HttpRequest request = buildSendRequest(accessToken, destination);
      final HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw EmailSenderException.becauseSendFailed(
            new IllegalStateException("Gmail API returned HTTP " + response.statusCode()));
      }
      log.info(LOG_SENT);
    } catch (final InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw EmailSenderException.becauseSendFailed(exception);
    } catch (final IOException | MessagingException exception) {
      throw EmailSenderException.becauseSendFailed(exception);
    }
  }

  private String refreshAccessToken() throws IOException, InterruptedException {
    final String form =
        "grant_type=refresh_token"
            + "&client_id=" + encode(config.clientId())
            + "&client_secret=" + encode(config.clientSecret())
            + "&refresh_token=" + encode(config.refreshToken());
    final HttpRequest request =
        HttpRequest.newBuilder(OAUTH_TOKEN_URI)
            .timeout(REQUEST_TIMEOUT)
            .header("content-type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build();
    final HttpResponse<String> response =
        httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new IllegalStateException("Google OAuth token endpoint returned HTTP " + response.statusCode());
    }
    final JsonNode body = objectMapper.readTree(response.body());
    final JsonNode accessToken = body.get("access_token");
    if (accessToken == null || accessToken.asText().isBlank()) {
      throw new IllegalStateException("Google OAuth response did not include an access token.");
    }
    return accessToken.asText();
  }

  private HttpRequest buildSendRequest(
      final String accessToken, final EmailDestinationModel destination)
      throws MessagingException, IOException {
    final String rawMessage = encodeMessage(destination);
    final String payload;
    try {
      payload = objectMapper.writeValueAsString(java.util.Map.of("raw", rawMessage));
    } catch (final JsonProcessingException exception) {
      throw new IOException("Could not encode the Gmail API request.", exception);
    }
    return HttpRequest.newBuilder(GMAIL_SEND_URI)
        .timeout(REQUEST_TIMEOUT)
        .header("authorization", "Bearer " + accessToken)
        .header("accept", "application/json")
        .header("content-type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(payload))
        .build();
  }

  private String encodeMessage(final EmailDestinationModel destination)
      throws MessagingException, IOException {
    final Session session = Session.getInstance(new Properties());
    final MimeMessage message = new MimeMessage(session);
    message.setFrom(new InternetAddress(config.senderAddress(), config.senderName(), "UTF-8"));
    message.addRecipient(
        Message.RecipientType.TO,
        new InternetAddress(
            destination.getDestinationEmail(), destination.getDestinationName(), "UTF-8"));
    message.setSubject(destination.getSubject(), StandardCharsets.UTF_8.name());
    message.setContent(destination.getBody(), CONTENT_TYPE_HTML);

    final ByteArrayOutputStream output = new ByteArrayOutputStream();
    message.writeTo(output);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(output.toByteArray());
  }

  private void validateConfig() {
    if (isBlank(config.clientId())
        || isBlank(config.clientSecret())
        || isBlank(config.refreshToken())
        || isBlank(config.senderAddress())
        || isBlank(config.senderName())) {
      throw new IllegalStateException(
          "GMAIL_CLIENT_ID, GMAIL_CLIENT_SECRET, GMAIL_REFRESH_TOKEN, GMAIL_SENDER_ADDRESS, "
              + "and GMAIL_SENDER_NAME are required when Gmail email is enabled.");
    }
  }

  private static String encode(final String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private static boolean isBlank(final String value) {
    return value == null || value.isBlank();
  }
}
