package com.jcaa.usersmanagement.infrastructure.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Properties;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AppProperties {

  private static final String PROPERTIES_FILE = "application.properties";
  private static final Map<String, String> ENVIRONMENT_KEYS = Map.of(
      "db.host", "DB_HOST",
      "db.port", "DB_PORT",
      "db.name", "DB_NAME",
      "db.username", "DB_USERNAME",
      "db.password", "DB_PASSWORD",
      "db.sslmode", "DB_SSLMODE",
      "app.email.enabled", "APP_EMAIL_ENABLED");
  private static final Pattern ENVIRONMENT_PLACEHOLDER =
      Pattern.compile("\\$\\{([^:}]+)(?::([^}]*))?}");

  private final Properties properties;

  public AppProperties() {
    this(AppProperties.class.getClassLoader().getResourceAsStream(PROPERTIES_FILE));
  }

  // Package-private — test entry point: inject null to simulate file-not-found,
  // a failing stream to simulate IOException, or a valid stream for the happy path.
  AppProperties(final InputStream stream) {
    this.properties = doLoad(stream);
  }

  private static Properties doLoad(final InputStream stream) {
    Objects.requireNonNull(stream, "File not found in classpath: " + PROPERTIES_FILE);
    final Properties props = new Properties();
    try (stream) {
      props.load(stream);
    } catch (final IOException exception) {
      throw ConfigurationException.becauseLoadFailed(exception);
    }
    return props;
  }

  public String get(final String key) {
    final String environmentKey = ENVIRONMENT_KEYS.get(key);
    final String environmentValue = environmentKey == null ? null : System.getenv(environmentKey);
    final String configuredValue = environmentValue == null ? properties.getProperty(key) : environmentValue;
    final String value = resolvePlaceholder(configuredValue);
    Objects.requireNonNull(value, "Property not found in " + PROPERTIES_FILE + ": " + key);
    return value;
  }

  public int getInt(final String key) {
    return Integer.parseInt(get(key));
  }

  public boolean getBoolean(final String key) {
    return Boolean.parseBoolean(get(key));
  }

  private static String resolvePlaceholder(final String value) {
    if (value == null) {
      return null;
    }
    final Matcher matcher = ENVIRONMENT_PLACEHOLDER.matcher(value);
    if (!matcher.matches()) {
      return value;
    }
    final String environmentValue = System.getenv(matcher.group(1));
    return environmentValue != null ? environmentValue : matcher.group(2);
  }
}
