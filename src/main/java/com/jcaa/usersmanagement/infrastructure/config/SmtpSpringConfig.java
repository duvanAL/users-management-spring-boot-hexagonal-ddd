package com.jcaa.usersmanagement.infrastructure.config;

import com.jcaa.usersmanagement.infrastructure.adapter.email.SmtpConfig;
import com.jcaa.usersmanagement.application.port.out.EmailSenderPort;
import com.jcaa.usersmanagement.infrastructure.adapter.email.GmailApiConfig;
import com.jcaa.usersmanagement.infrastructure.adapter.email.GmailApiEmailSenderAdapter;
import com.jcaa.usersmanagement.infrastructure.adapter.email.JavaMailEmailSenderAdapter;
import com.jcaa.usersmanagement.infrastructure.adapter.email.NoOpEmailSenderAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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

  @Value("${app.email.enabled:false}")
  private boolean emailEnabled;

  @Bean
  public SmtpConfig smtpConfig() {
    return new SmtpConfig(smtpHost, smtpPort, smtpUsername, smtpPassword, smtpFromAddress, smtpFromName);
  }

  @Bean
  public EmailSenderPort emailSender(final SmtpConfig config) {
    if (!emailEnabled) {
      return new NoOpEmailSenderAdapter();
    }
    if ("gmail".equalsIgnoreCase(emailProvider)) {
      return new GmailApiEmailSenderAdapter(
          new GmailApiConfig(
              gmailClientId, gmailClientSecret, gmailRefreshToken,
              gmailSenderAddress, gmailSenderName));
    }
    if ("smtp".equalsIgnoreCase(emailProvider)) {
      return new JavaMailEmailSenderAdapter(config);
    }
    throw new IllegalStateException("APP_EMAIL_PROVIDER must be either 'gmail' or 'smtp'.");
  }
}

