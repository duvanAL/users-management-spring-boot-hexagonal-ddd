package com.jcaa.usersmanagement.infrastructure.config;

import com.jcaa.usersmanagement.application.port.out.EmailSenderPort;
import com.jcaa.usersmanagement.infrastructure.adapter.email.BrevoConfig;
import com.jcaa.usersmanagement.infrastructure.adapter.email.BrevoEmailSenderAdapter;
import com.jcaa.usersmanagement.infrastructure.adapter.email.BrevoResilience;
import com.jcaa.usersmanagement.infrastructure.adapter.email.BrevoResilienceConfig;
import com.jcaa.usersmanagement.infrastructure.adapter.email.GmailApiConfig;
import com.jcaa.usersmanagement.infrastructure.adapter.email.GmailApiEmailSenderAdapter;
import com.jcaa.usersmanagement.infrastructure.adapter.email.KafkaEmailSenderAdapter;
import com.jcaa.usersmanagement.infrastructure.adapter.email.JavaMailEmailSenderAdapter;
import com.jcaa.usersmanagement.infrastructure.adapter.email.NoOpEmailSenderAdapter;
import com.jcaa.usersmanagement.infrastructure.adapter.email.SmtpConfig;
import com.jcaa.usersmanagement.infrastructure.messaging.kafka.KafkaMessagePublisher;
import com.jcaa.usersmanagement.infrastructure.messaging.kafka.KafkaNotificationProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class SmtpSpringConfig {

  private static final String PROP_SMTP_HOST        = "${smtp.host}";
  private static final String PROP_SMTP_PORT        = "${smtp.port}";
  private static final String PROP_SMTP_USERNAME    = "${smtp.username}";
  private static final String PROP_SMTP_PASSWORD    = "${smtp.password}";
  private static final String PROP_SMTP_FROM        = "${smtp.from.address}";
  private static final String PROP_SMTP_FROM_NAME   = "${smtp.from.name}";
  private static final String PROP_EMAIL_PROVIDER = "${app.email.provider:gmail}";
  private static final String PROP_GMAIL_CLIENT_ID = "${email.gmail.client-id:}";
  private static final String PROP_GMAIL_CLIENT_SECRET = "${email.gmail.client-secret:}";
  private static final String PROP_GMAIL_REFRESH_TOKEN = "${email.gmail.refresh-token:}";
  private static final String PROP_GMAIL_SENDER_ADDRESS = "${email.gmail.sender-address:}";
  private static final String PROP_GMAIL_SENDER_NAME = "${email.gmail.sender-name:Gestion de Usuarios}";
  private static final String PROP_BREVO_BASE_URL = "${brevo.base-url:https://api.brevo.com}";
  private static final String PROP_BREVO_API_KEY = "${brevo.api-key:}";
  private static final String PROP_BREVO_FROM_ADDRESS = "${brevo.from.address:}";
  private static final String PROP_BREVO_FROM_NAME = "${brevo.from.name:Gestion de Usuarios}";
  private static final String PROP_BREVO_CONNECT_TIMEOUT_MS = "${brevo.timeout.connect-ms:3000}";
  private static final String PROP_BREVO_READ_TIMEOUT_MS = "${brevo.timeout.read-ms:10000}";
  private static final String PROP_BREVO_RETRY_MAX_ATTEMPTS = "${brevo.retry.max-attempts:3}";
  private static final String PROP_BREVO_RETRY_INITIAL_DELAY_MS = "${brevo.retry.initial-delay-ms:500}";
  private static final String PROP_BREVO_RETRY_MULTIPLIER = "${brevo.retry.multiplier:2.0}";
  private static final String PROP_BREVO_CIRCUIT_FAILURE_THRESHOLD =
      "${brevo.circuit-breaker.failure-rate-threshold:50}";
  private static final String PROP_BREVO_CIRCUIT_WINDOW_SIZE =
      "${brevo.circuit-breaker.sliding-window-size:10}";
  private static final String PROP_BREVO_CIRCUIT_MINIMUM_CALLS =
      "${brevo.circuit-breaker.minimum-calls:5}";
  private static final String PROP_BREVO_CIRCUIT_OPEN_DURATION_MS =
      "${brevo.circuit-breaker.open-duration-ms:30000}";

  @Value(PROP_SMTP_HOST)
  private String smtpHost;

  @Value(PROP_SMTP_PORT)
  private int smtpPort;

  @Value(PROP_SMTP_USERNAME)
  private String smtpUsername;

  @Value(PROP_SMTP_PASSWORD)
  private String smtpPassword;

  @Value(PROP_SMTP_FROM)
  private String smtpFromAddress;

  @Value(PROP_SMTP_FROM_NAME)
  private String smtpFromName;

  @Value(PROP_EMAIL_PROVIDER)
  private String emailProvider;

  @Value(PROP_GMAIL_CLIENT_ID)
  private String gmailClientId;

  @Value(PROP_GMAIL_CLIENT_SECRET)
  private String gmailClientSecret;

  @Value(PROP_GMAIL_REFRESH_TOKEN)
  private String gmailRefreshToken;

  @Value(PROP_GMAIL_SENDER_ADDRESS)
  private String gmailSenderAddress;

  @Value(PROP_GMAIL_SENDER_NAME)
  private String gmailSenderName;

  @Value(PROP_BREVO_BASE_URL)
  private String brevoBaseUrl;

  @Value(PROP_BREVO_API_KEY)
  private String brevoApiKey;

  @Value(PROP_BREVO_FROM_ADDRESS)
  private String brevoFromAddress;

  @Value(PROP_BREVO_FROM_NAME)
  private String brevoFromName;

  @Value(PROP_BREVO_CONNECT_TIMEOUT_MS)
  private long brevoConnectTimeoutMs;

  @Value(PROP_BREVO_READ_TIMEOUT_MS)
  private long brevoReadTimeoutMs;

  @Value(PROP_BREVO_RETRY_MAX_ATTEMPTS)
  private int brevoRetryMaxAttempts;

  @Value(PROP_BREVO_RETRY_INITIAL_DELAY_MS)
  private long brevoRetryInitialDelayMs;

  @Value(PROP_BREVO_RETRY_MULTIPLIER)
  private double brevoRetryMultiplier;

  @Value(PROP_BREVO_CIRCUIT_FAILURE_THRESHOLD)
  private float brevoCircuitFailureThreshold;

  @Value(PROP_BREVO_CIRCUIT_WINDOW_SIZE)
  private int brevoCircuitWindowSize;

  @Value(PROP_BREVO_CIRCUIT_MINIMUM_CALLS)
  private int brevoCircuitMinimumCalls;

  @Value(PROP_BREVO_CIRCUIT_OPEN_DURATION_MS)
  private long brevoCircuitOpenDurationMs;

  @Value("${app.email.enabled:false}")
  private boolean emailEnabled;

  @Value("${app.kafka.enabled:false}")
  private boolean kafkaEnabled;

  @Value("${app.runtime.role:api}")
  private String runtimeRole;

  @Bean
  public SmtpConfig smtpConfig() {
    return new SmtpConfig(smtpHost, smtpPort, smtpUsername, smtpPassword, smtpFromAddress, smtpFromName);
  }

  @Bean
  public EmailSenderPort emailSender(
      final SmtpConfig config,
      final ObjectProvider<KafkaMessagePublisher> kafkaPublisherProvider,
      final ObjectProvider<KafkaNotificationProperties> kafkaPropertiesProvider) {
    if (kafkaEnabled && "api".equalsIgnoreCase(runtimeRole)) {
      return new KafkaEmailSenderAdapter(
          kafkaPublisherProvider.getObject(), kafkaPropertiesProvider.getObject());
    }
    if ("notification-worker".equalsIgnoreCase(runtimeRole)) {
      if (!kafkaEnabled || !emailEnabled) {
        throw new IllegalStateException(
            "The notification worker requires APP_KAFKA_ENABLED=true and APP_EMAIL_ENABLED=true.");
      }
      return configuredDirectEmailSender(config);
    }
    if (!emailEnabled) {
      return new NoOpEmailSenderAdapter();
    }
    return configuredDirectEmailSender(config);
  }

  private EmailSenderPort configuredDirectEmailSender(final SmtpConfig config) {
    if ("gmail".equalsIgnoreCase(emailProvider)) {
      return new GmailApiEmailSenderAdapter(
          new GmailApiConfig(
              gmailClientId, gmailClientSecret, gmailRefreshToken,
              gmailSenderAddress, gmailSenderName));
    }
    if ("smtp".equalsIgnoreCase(emailProvider)) {
      return new JavaMailEmailSenderAdapter(config);
    }
    if ("brevo".equalsIgnoreCase(emailProvider)) {
      final Duration connectTimeout = Duration.ofMillis(brevoConnectTimeoutMs);
      final Duration readTimeout = Duration.ofMillis(brevoReadTimeoutMs);
      final BrevoConfig brevoConfig =
          new BrevoConfig(
              brevoBaseUrl,
              brevoApiKey,
              brevoFromAddress,
              brevoFromName,
              connectTimeout,
              readTimeout);
      final SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
      requestFactory.setConnectTimeout(connectTimeout);
      requestFactory.setReadTimeout(readTimeout);
      final RestClient restClient =
          RestClient.builder()
              .baseUrl(brevoBaseUrl)
              .requestFactory(requestFactory)
              .defaultHeader("api-key", brevoApiKey)
              .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
              .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
              .build();
      final BrevoResilienceConfig resilienceConfig =
          new BrevoResilienceConfig(
              brevoRetryMaxAttempts,
              Duration.ofMillis(brevoRetryInitialDelayMs),
              brevoRetryMultiplier,
              brevoCircuitFailureThreshold,
              brevoCircuitWindowSize,
              brevoCircuitMinimumCalls,
              Duration.ofMillis(brevoCircuitOpenDurationMs));
      final Retry retry = BrevoResilience.retry(resilienceConfig);
      final CircuitBreaker circuitBreaker = BrevoResilience.circuitBreaker(resilienceConfig);
      return new BrevoEmailSenderAdapter(brevoConfig, restClient, retry, circuitBreaker);
    }
    throw new IllegalStateException(
        "APP_EMAIL_PROVIDER must be 'gmail', 'smtp' or 'brevo'.");
  }
}

