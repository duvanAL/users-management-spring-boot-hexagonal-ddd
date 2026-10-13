package com.jcaa.usersmanagement.infrastructure.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "valkey")
public record ValkeyProperties(
        String host, int port, String username, String password,
        boolean sslEnabled, long connectTimeoutMs, long commandTimeoutMs) {}
