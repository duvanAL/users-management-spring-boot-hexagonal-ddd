package com.jcaa.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcaa.events.jdbc.JdbcEventOutbox;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class ProjectionVersionIntegrationTest {
  @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:17-alpine");

  @Test
  void versionsAndBusinessChangesRollbackTogetherAndCountersSurviveDeletion() throws Exception {
    var source = new DriverManagerDataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
    var jdbc = new JdbcTemplate(source);
    jdbc.execute("CREATE TABLE domain_event_outbox (event_id UUID PRIMARY KEY, event_json TEXT)");
    jdbc.execute("CREATE TABLE domain_event_outbox_versions (entity_key VARCHAR(300) PRIMARY KEY, version BIGINT NOT NULL)");
    jdbc.execute("CREATE TABLE business (id TEXT PRIMARY KEY)");
    var mapper = new ObjectMapper().findAndRegisterModules();
    var outbox = new JdbcEventOutbox(jdbc, mapper, "domain_event_outbox");
    var transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
    var event = DomainEvent.create("user.created", Map.of("userId", "demo"));
    assertThatThrownBy(() -> outbox.enqueue(event)).isInstanceOf(IllegalStateException.class);
    transaction.executeWithoutResult(status -> {
      jdbc.update("INSERT INTO business VALUES ('demo')");
      outbox.enqueue(event);
    });
    assertThat(mapper.readTree(jdbc.queryForObject("SELECT event_json FROM domain_event_outbox", String.class))
        .path("payload").path("projectionVersion").asText()).isEqualTo("1");
    assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
      jdbc.update("DELETE FROM business");
      outbox.enqueue(DomainEvent.create("user.deleted", Map.of("userId", "demo")));
      throw new IllegalStateException("Synthetic rollback");
    })).isInstanceOf(IllegalStateException.class);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM business", Long.class)).isEqualTo(1L);
    assertThat(jdbc.queryForObject("SELECT version FROM domain_event_outbox_versions", Long.class)).isEqualTo(1L);
    transaction.executeWithoutResult(status -> {
      jdbc.update("DELETE FROM business");
      outbox.enqueue(DomainEvent.create("user.deleted", Map.of("userId", "demo")));
    });
    assertThat(jdbc.queryForObject("SELECT version FROM domain_event_outbox_versions", Long.class)).isEqualTo(2L);
  }
}
