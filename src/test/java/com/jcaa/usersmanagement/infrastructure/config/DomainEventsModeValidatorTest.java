package com.jcaa.usersmanagement.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

class DomainEventsModeValidatorTest {
  @Test
  void requiresKafkaAndAnIndependentWorker() {
    assertThatCode(() -> new DomainEventsModeValidator(true, true, false))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> new DomainEventsModeValidator(true, false, false))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> new DomainEventsModeValidator(true, true, true))
        .isInstanceOf(IllegalStateException.class);
  }
}
