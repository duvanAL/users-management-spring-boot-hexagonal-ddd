package com.jcaa.usersmanagement.infrastructure.adapter.email;

import com.jcaa.usersmanagement.application.port.out.EmailSenderPort;
import com.jcaa.usersmanagement.domain.model.EmailDestinationModel;
import lombok.extern.slf4j.Slf4j;

/** Email adapter used when SMTP is intentionally disabled for local development. */
@Slf4j
public final class NoOpEmailSenderAdapter implements EmailSenderPort {

  @Override
  public void send(final EmailDestinationModel destination) {
    log.info("Correo omitido porque APP_EMAIL_ENABLED=false. Destinatario={}",
        destination.getDestinationEmail());
  }
}
