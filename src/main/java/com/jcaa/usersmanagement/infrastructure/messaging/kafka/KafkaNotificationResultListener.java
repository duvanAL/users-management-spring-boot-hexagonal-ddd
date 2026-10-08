package com.jcaa.usersmanagement.infrastructure.messaging.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** API-side consumer for notification outcomes. */
@Slf4j
@Component
@ConditionalOnExpression(
    "'${app.kafka.enabled:false}' == 'true' and '${app.runtime.role:api}' == 'api'")
public class KafkaNotificationResultListener {

  @KafkaListener(
      topics = "${app.kafka.result-topic}",
      groupId = "${app.kafka.users-group-id}",
      containerFactory = "notificationResultListenerContainerFactory")
  public void onResult(final NotificationResultMessage result) {
    if (result.status() == NotificationResultMessage.Status.SENT) {
      log.info("Notificación procesada. id={} status={}", result.notificationId(), result.status());
    } else {
      log.warn(
          "Notificación fallida y enviada a DLQ. id={} code={}",
          result.notificationId(),
          result.failureCode());
    }
  }
}
