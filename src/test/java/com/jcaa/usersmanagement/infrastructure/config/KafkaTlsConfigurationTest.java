package com.jcaa.usersmanagement.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcaa.usersmanagement.infrastructure.messaging.kafka.KafkaNotificationProperties;
import org.apache.kafka.common.config.SslConfigs;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.test.util.ReflectionTestUtils;

class KafkaTlsConfigurationTest {
  @Test
  void usesSuppliedPemCaWithoutDisablingHostnameValidation() {
    final KafkaMessagingConfiguration config = new KafkaMessagingConfiguration();
    ReflectionTestUtils.setField(config, "sslTrustCertificates", "CERTIFICATE\\nDATA");
    final var factory = (DefaultKafkaProducerFactory<String, Object>)
        config.kafkaProducerFactory(properties(), new ObjectMapper());
    assertThat(factory.getConfigurationProperties())
        .containsEntry(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, "PEM")
        .containsEntry(SslConfigs.SSL_TRUSTSTORE_CERTIFICATES_CONFIG, "CERTIFICATE\nDATA")
        .containsEntry(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, "https");
  }

  @Test
  void preservesDefaultTrustWhenCustomCaIsAbsent() {
    final var factory = (DefaultKafkaProducerFactory<String, Object>)
        new KafkaMessagingConfiguration().kafkaProducerFactory(properties(), new ObjectMapper());
    assertThat(factory.getConfigurationProperties())
        .doesNotContainKey(SslConfigs.SSL_TRUSTSTORE_CERTIFICATES_CONFIG)
        .containsEntry(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, "https");
  }

  private KafkaNotificationProperties properties() {
    return new KafkaNotificationProperties(true, "localhost:9093", "test", "test",
        "SASL_SSL", "SCRAM-SHA-256", "request", "result", "dlq", "api", "worker", 1000);
  }
}
