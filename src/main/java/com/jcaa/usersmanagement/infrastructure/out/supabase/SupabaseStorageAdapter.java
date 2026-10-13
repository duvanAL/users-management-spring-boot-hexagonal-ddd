package com.jcaa.usersmanagement.infrastructure.out.supabase;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.jcaa.usersmanagement.application.port.out.StoragePort;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@ConditionalOnProperty(name = "app.storage.supabase.enabled", havingValue = "true")
public class SupabaseStorageAdapter implements StoragePort {

    private final String supabaseUrl;
    private final String bucketName;
    private final String serviceRoleKey;
    private final RestTemplate restTemplate;

    public SupabaseStorageAdapter(
            final SupabaseStorageProperties properties,
            @Qualifier("supabaseRestTemplate") final RestTemplate restTemplate) {
        this.supabaseUrl = properties.url().replaceAll("/+$", "");
        this.bucketName = properties.bucket();
        this.serviceRoleKey = properties.serviceRoleKey();
        this.restTemplate = restTemplate;
    }

    @Override
    public void uploadAvatar(final String userId, final byte[] fileBytes, final String contentType) {
        final String url = objectUrl(userId);
        final HttpHeaders headers = authenticatedHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        headers.set("x-upsert", "true");
        restTemplate.exchange(url, HttpMethod.PUT, new HttpEntity<>(fileBytes, headers), Void.class);
    }

    @Override
    public String getAvatarSignedUrl(final String userId) {
        final String url = supabaseUrl + "/storage/v1/object/sign/" + bucketName + "/users/" + userId + "/avatar";
        final HttpHeaders headers = authenticatedHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        final SignedUrlResponse response = restTemplate.postForObject(
                url, new HttpEntity<>("{\"expiresIn\":300}", headers), SignedUrlResponse.class);
        if (response == null || !org.springframework.util.StringUtils.hasText(response.signedUrl())) {
            throw new IllegalStateException("Supabase returned an empty signed URL");
        }
        final String signedPath = response.signedUrl().startsWith("/")
                ? response.signedUrl()
                : "/" + response.signedUrl();
        return supabaseUrl + "/storage/v1" + signedPath;
    }

    private String objectUrl(final String userId) {
        return supabaseUrl + "/storage/v1/object/" + bucketName + "/users/" + userId + "/avatar";
    }

    private HttpHeaders authenticatedHeaders() {
        final HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(serviceRoleKey);
        headers.set("apikey", serviceRoleKey);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    private record SignedUrlResponse(@JsonProperty("signedURL") String signedUrl) {}
}
