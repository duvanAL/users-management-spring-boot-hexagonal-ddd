package com.jcaa.usersmanagement.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

class RenderBlueprintPolicyTest {
  @Test
  void renderMustNotProvisionPostgresOrReferenceManagedDatabases() throws IOException {
    try (var input = Files.newInputStream(Path.of("render.yaml"))) {
      Map<?, ?> blueprint = new Yaml(new SafeConstructor(new LoaderOptions())).load(input);
      assertThat(blueprint.containsKey("databases"))
          .as("Render must host only the API; production PostgreSQL is external in Supabase")
          .isFalse();
      rejectManagedDatabaseReference(blueprint);
    }
  }

  private void rejectManagedDatabaseReference(Object node) {
    if (node instanceof Map<?, ?> mapping) {
      assertThat(mapping.containsKey("fromDatabase"))
          .as("Use explicit external DB_* settings, never a Render database reference")
          .isFalse();
      mapping.values().forEach(this::rejectManagedDatabaseReference);
    } else if (node instanceof List<?> sequence) {
      sequence.forEach(this::rejectManagedDatabaseReference);
    }
  }
}
