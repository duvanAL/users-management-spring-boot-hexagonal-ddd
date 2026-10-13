package com.jcaa.usersmanagement.infrastructure.out.supabase;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.storage.supabase.enabled", havingValue = "true")
public class SupabaseStorageHttpConfig {

    @Bean("supabaseRestTemplate")
    RestTemplate supabaseRestTemplate(
            @Value("${supabase.connect-timeout-ms:3000}") final int connectTimeoutMs,
            @Value("${supabase.read-timeout-ms:5000}") final int readTimeoutMs) {
        if (connectTimeoutMs <= 0 || readTimeoutMs <= 0) {
            throw new IllegalStateException("Supabase HTTP timeouts must be greater than zero");
        }
        final SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        return new RestTemplate(requestFactory);
    }
}
