package com.jcaa.usersmanagement.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcaa.usersmanagement.infrastructure.messaging.kafka.KafkaMessagePublisher;
import com.jcaa.usersmanagement.infrastructure.messaging.kafka.KafkaNotificationProperties;
import com.jcaa.usersmanagement.infrastructure.messaging.kafka.NotificationDeadLetterMessage;
import com.jcaa.usersmanagement.infrastructure.messaging.kafka.NotificationRequestMessage;
import com.jcaa.usersmanagement.infrastructure.messaging.kafka.NotificationResultMessage;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.util.backoff.FixedBackOff;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

@Configuration(proxyBeanMethods = false)
@EnableKafka
@EnableConfigurationProperties(KafkaNotificationProperties.class)
@ConditionalOnProperty(prefix = "app.kafka", name = "enabled", havingValue = "true")
public class KafkaMessagingConfiguration {

  @Bean
  ProducerFactory<String, Object> kafkaProducerFactory(
      final KafkaNotificationProperties properties, final ObjectMapper objectMapper) {
    final Map<String, Object> config = commonClientProperties(properties);
    config.put(ProducerConfig.ACKS_CONFIG, "all");
    config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
    config.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);
    config.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
    return new DefaultKafkaProducerFactory<>(
        config, new StringSerializer(), new JsonSerializer<>(objectMapper));
  }

  @Bean
  KafkaTemplate<String, Object> kafkaTemplate(final ProducerFactory<String, Object> factory) {
    return new KafkaTemplate<>(factory);
  }

  @Bean
  KafkaMessagePublisher kafkaMessagePublisher(
      final KafkaTemplate<String, Object> kafkaTemplate,
      final KafkaNotificationProperties properties) {
    return new KafkaMessagePublisher(kafkaTemplate, properties);
  }

  @Bean("notificationRequestListenerContainerFactory")
  ConcurrentKafkaListenerContainerFactory<String, NotificationRequestMessage>
      notificationRequestListenerContainerFactory(
          final KafkaNotificationProperties properties,
          final ObjectMapper objectMapper,
          final KafkaMessagePublisher publisher,
          @Value("${app.runtime.role:api}") final String runtimeRole,
          @Value("${app.kafka.listener-auto-startup:true}") final boolean listenerAutoStartup) {
    final String groupId =
        "notification-worker".equalsIgnoreCase(runtimeRole)
            ? properties.notifyGroupId()
            : properties.usersGroupId();
    final Map<String, Object> config = consumerProperties(properties, groupId);
    final JsonDeserializer<NotificationRequestMessage> valueDeserializer =
        new JsonDeserializer<>(NotificationRequestMessage.class, objectMapper);
    valueDeserializer.addTrustedPackages("com.jcaa.usersmanagement.infrastructure.messaging.kafka");
    final DefaultKafkaConsumerFactory<String, NotificationRequestMessage> consumerFactory =
        new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), valueDeserializer);
    final ConcurrentKafkaListenerContainerFactory<String, NotificationRequestMessage> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(consumerFactory);
    factory.setAutoStartup(listenerAutoStartup);
    if ("notification-worker".equalsIgnoreCase(runtimeRole)) {
      final ConsumerRecordRecoverer recoverer =
          (record, exception) -> {
            if (!(record.value() instanceof NotificationRequestMessage request)) {
              throw new IllegalStateException("Unable to recover malformed Kafka notification.");
            }
            publisher.publish(
                properties.deadLetterTopic(),
                request.notificationId().toString(),
                new NotificationDeadLetterMessage(
                    NotificationRequestMessage.CURRENT_SCHEMA_VERSION,
                    request,
                    "PROCESSING_RETRIES_EXHAUSTED",
                    Instant.now()));
          };
      factory.setCommonErrorHandler(new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2L)));
    }
    return factory;
  }

  @Bean("notificationResultListenerContainerFactory")
  ConcurrentKafkaListenerContainerFactory<String, NotificationResultMessage>
      notificationResultListenerContainerFactory(
          final KafkaNotificationProperties properties,
          final ObjectMapper objectMapper,
          @Value("${app.kafka.listener-auto-startup:true}") final boolean listenerAutoStartup) {
    final Map<String, Object> config =
        consumerProperties(properties, properties.usersGroupId());
    final JsonDeserializer<NotificationResultMessage> valueDeserializer =
        new JsonDeserializer<>(NotificationResultMessage.class, objectMapper);
    valueDeserializer.addTrustedPackages("com.jcaa.usersmanagement.infrastructure.messaging.kafka");
    final DefaultKafkaConsumerFactory<String, NotificationResultMessage> consumerFactory =
        new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), valueDeserializer);
    final ConcurrentKafkaListenerContainerFactory<String, NotificationResultMessage> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(consumerFactory);
    factory.setAutoStartup(listenerAutoStartup);
    factory.setCommonErrorHandler(
        new DefaultErrorHandler(
            (record, exception) ->
                org.slf4j.LoggerFactory.getLogger(KafkaMessagingConfiguration.class)
                    .error(
                        "Could not process notification result; skipping after retries. type={}",
                        exception.getClass().getSimpleName()),
            new FixedBackOff(1000L, 2L)));
    return factory;
  }

  private static Map<String, Object> commonClientProperties(
      final KafkaNotificationProperties properties) {
    final Map<String, Object> config = new HashMap<>();
    config.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, properties.bootstrapServers());
    config.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, properties.securityProtocol());
    config.put(SaslConfigs.SASL_MECHANISM, properties.saslMechanism());
    config.put(
        SaslConfigs.SASL_JAAS_CONFIG,
        "org.apache.kafka.common.security.scram.ScramLoginModule required username=\""
            + escapeJaas(properties.username())
            + "\" password=\""
            + escapeJaas(properties.password())
            + "\";");
    config.put("ssl.endpoint.identification.algorithm", "https");
    return config;
  }

  private static Map<String, Object> consumerProperties(
      final KafkaNotificationProperties properties, final String groupId) {
    final Map<String, Object> config = commonClientProperties(properties);
    config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    return config;
  }

  private static String escapeJaas(final String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
