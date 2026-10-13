package com.jcaa.usersmanagement.infrastructure.out.supabase;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnProperty(name = "app.storage.supabase.enabled", havingValue = "true")
public record SupabaseStorageProperties(
        @Value("${supabase.url}") String url,
        @Value("${supabase.storage.bucket}") String bucket,
        @Value("${supabase.service-role-key}") String serviceRoleKey) {

    public SupabaseStorageProperties {
        if (!StringUtils.hasText(url) || !StringUtils.hasText(bucket) || !StringUtils.hasText(serviceRoleKey)) {
            throw new IllegalStateException("Supabase Storage is enabled but its configuration is incomplete");
        }
    }
}
