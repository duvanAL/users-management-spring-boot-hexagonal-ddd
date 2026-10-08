package com.jcaa.usersmanagement.infrastructure.adapter.email;

import com.jcaa.usersmanagement.application.port.out.EmailSenderPort;
import com.jcaa.usersmanagement.domain.exception.EmailSenderException;
import com.jcaa.usersmanagement.domain.model.EmailDestinationModel;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** Sends transactional email through Brevo's HTTPS API. */
@Slf4j
public final class BrevoEmailSenderAdapter implements EmailSenderPort {

  private static final String SEND_EMAIL_PATH = "/v3/smtp/email";
  private static final String LOG_SENT =
      "[BrevoEmailSenderAdapter] Brevo aceptó un correo transaccional.";

  private final BrevoConfig config;
  private final RestClient restClient;

  public BrevoEmailSenderAdapter(final BrevoConfig config, final RestClient restClient) {
    this.config = config;
    this.restClient = restClient;
  }

  @Override
  public void send(final EmailDestinationModel destination) {
    final BrevoEmailRequest request =
        new BrevoEmailRequest(
            new BrevoEmailRequest.Sender(config.fromAddress(), config.fromName()),
            List.of(
                new BrevoEmailRequest.Recipient(
                    destination.getDestinationEmail(), destination.getDestinationName())),
            destination.getSubject(),
            destination.getBody());
    try {
      restClient.post().uri(SEND_EMAIL_PATH).body(request).retrieve().toBodilessEntity();
      log.info(LOG_SENT);
    } catch (final RestClientException exception) {
      // Do not log the response body: provider errors can contain recipient or account details.
      final String failure =
          exception instanceof RestClientResponseException responseException
              ? "HTTP " + responseException.getStatusCode().value()
              : exception.getClass().getSimpleName();
      throw EmailSenderException.becauseSendFailed(
          new IllegalStateException("Brevo API request failed: " + failure + "."));
    }
  }
}
