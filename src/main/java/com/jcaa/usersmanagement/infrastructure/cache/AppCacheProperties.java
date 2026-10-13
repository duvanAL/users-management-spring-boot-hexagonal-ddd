package com.jcaa.usersmanagement.infrastructure.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.cache")
public record AppCacheProperties(boolean enabled, long ttlSeconds) {}