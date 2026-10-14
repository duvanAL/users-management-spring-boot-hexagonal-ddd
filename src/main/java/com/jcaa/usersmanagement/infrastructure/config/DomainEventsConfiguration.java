package com.jcaa.usersmanagement.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcaa.events.jdbc.JdbcEventOutbox;
import com.jcaa.events.jdbc.OutboxRelay;
import com.jcaa.usersmanagement.application.port.out.DomainEventPublisherPort;
import com.jcaa.usersmanagement.infrastructure.messaging.kafka.KafkaMessagePublisher;
import com.jcaa.usersmanagement.infrastructure.messaging.kafka.KafkaNotificationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnExpression(
    "'${app.domain-events.enabled:false}' == 'true' and '${app.kafka.enabled:false}' == 'true' and"
        + " '${app.runtime.role:api}' == 'api'")
public class DomainEventsConfiguration {
  @Bean
  JdbcEventOutbox domainEventOutbox(JdbcTemplate jdbc, ObjectMapper mapper) {
    return new JdbcEventOutbox(jdbc, mapper, "domain_event_outbox");
  }

  @Bean
  DomainEventPublisherPort domainEventPublisher(JdbcEventOutbox outbox) {
    return outbox::enqueue;
  }

  @Bean
  OutboxRelay domainEventRelay(
      JdbcEventOutbox outbox,
      KafkaMessagePublisher publisher,
      KafkaNotificationProperties kafka,
      ObjectMapper mapper) {
    return new OutboxRelay(
        outbox,
        (key, body) -> {
          try {
            publisher.publish(kafka.requestTopic(), key, mapper.readTree(body));
          } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Invalid outbox event.", e);
          }
        });
  }
}
