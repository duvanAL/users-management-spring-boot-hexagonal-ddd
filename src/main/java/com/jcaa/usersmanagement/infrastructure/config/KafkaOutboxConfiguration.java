package com.jcaa.usersmanagement.infrastructure.config;

import com.jcaa.usersmanagement.infrastructure.messaging.kafka.EmailOutboxRepository;
import com.jcaa.usersmanagement.infrastructure.messaging.kafka.KafkaEmailOutboxPublisher;
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
    "'${app.kafka.enabled:false}' == 'true' and '${app.runtime.role:api}' == 'api'")
public class KafkaOutboxConfiguration {

  @Bean
  EmailOutboxRepository emailOutboxRepository(final JdbcTemplate jdbcTemplate) {
    return new EmailOutboxRepository(jdbcTemplate);
  }

  @Bean
  KafkaEmailOutboxPublisher kafkaEmailOutboxPublisher(
      final EmailOutboxRepository repository,
      final KafkaMessagePublisher publisher,
      final KafkaNotificationProperties properties) {
    return new KafkaEmailOutboxPublisher(repository, publisher, properties);
  }
}
