package com.jcaa.usersmanagement.infrastructure.adapter.email;

public record GmailApiConfig(
    String clientId,
    String clientSecret,
    String refreshToken,
    String senderAddress,
    String senderName) {}
