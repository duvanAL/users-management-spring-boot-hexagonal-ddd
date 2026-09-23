package com.jcaa.usersmanagement.infrastructure.config;

public final class ConfigurationException extends RuntimeException {

  private static final String MESSAGE_LOAD = "Failed to load the application configuration.";
  private static final String MESSAGE_INVALID = "Invalid or missing configuration property: %s.";

  private ConfigurationException(final String message, final Throwable cause) {
    super(message, cause);
  }

  public static ConfigurationException becauseLoadFailed(final Throwable cause) {
    return new ConfigurationException(MESSAGE_LOAD, cause);
  }

  public static ConfigurationException becauseInvalidProperty(final String property) {
    return new ConfigurationException(String.format(MESSAGE_INVALID, property), null);
  }
}
