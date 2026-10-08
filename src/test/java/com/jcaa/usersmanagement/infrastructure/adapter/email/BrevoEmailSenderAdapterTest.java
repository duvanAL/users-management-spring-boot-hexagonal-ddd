package com.jcaa.usersmanagement.infrastructure.adapter.email;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.jcaa.usersmanagement.domain.exception.EmailSenderException;
import com.jcaa.usersmanagement.domain.model.EmailDestinationModel;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class BrevoEmailSenderAdapterTest {

  private static final String API_KEY = "fake-test-api-key";
  private static final String BASE_URL = "https://api.brevo.test";

  @Test
  void shouldSendTransactionalEmailUsingBrevoRestContract() {
    final RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
    final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo(BASE_URL + "/v3/smtp/email"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("api-key", API_KEY))
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(
            content()
                .json(
                    """
                    {
                      "sender": {"email":"no-reply@example.com", "name":"Users API"},
                      "to": [{"email":"ada@example.com", "name":"Ada"}],
                      "subject":"Account updated",
                      "htmlContent":"<p>Updated</p>"
                    }
                    """))
        .andRespond(withSuccess());

    adapter(builder).send(destination());

    server.verify();
  }

  @Test
  void shouldTranslateProviderHttpErrorsWithoutExposingResponseBody() {
    final RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
    final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo(BASE_URL + "/v3/smtp/email"))
        .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("sensitive provider response"));

    assertThatThrownBy(() -> adapter(builder).send(destination()))
        .isInstanceOf(EmailSenderException.class)
        .hasMessage("La notificación por correo no pudo ser enviada.")
        .hasRootCauseMessage("Brevo API request failed: HTTP 429.")
        .hasMessageNotContaining("sensitive provider response");

    server.verify();
  }

  private static BrevoEmailSenderAdapter adapter(final RestClient.Builder builder) {
    final BrevoConfig config =
        new BrevoConfig(
            BASE_URL,
            API_KEY,
            "no-reply@example.com",
            "Users API",
            Duration.ofSeconds(2),
            Duration.ofSeconds(5));
    final RestClient client = builder.defaultHeader("api-key", API_KEY).build();
    return new BrevoEmailSenderAdapter(config, client);
  }

  private static EmailDestinationModel destination() {
    return new EmailDestinationModel(
        "ada@example.com", "Ada", "Account updated", "<p>Updated</p>");
  }
}
