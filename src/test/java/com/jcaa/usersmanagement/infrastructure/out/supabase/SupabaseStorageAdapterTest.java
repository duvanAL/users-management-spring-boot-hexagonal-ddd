package com.jcaa.usersmanagement.infrastructure.out.supabase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class SupabaseStorageAdapterTest {

    private static final String BASE_URL = "https://project.supabase.co";
    private static final String BUCKET = "users-management-bucket";
    private static final String SERVICE_ROLE_KEY = "test-service-role-key";

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private SupabaseStorageAdapter adapter;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        adapter = new SupabaseStorageAdapter(
                new SupabaseStorageProperties(BASE_URL + "/", BUCKET, SERVICE_ROLE_KEY), restTemplate);
    }

    @Test
    void uploadSendsBothSupabaseAuthenticationHeaders() {
        server.expect(requestTo(BASE_URL + "/storage/v1/object/" + BUCKET + "/users/123/avatar"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header("Authorization", "Bearer " + SERVICE_ROLE_KEY))
                .andExpect(header("apikey", SERVICE_ROLE_KEY))
                .andExpect(header("Content-Type", MediaType.IMAGE_PNG_VALUE))
                .andRespond(withSuccess());

        adapter.uploadAvatar("123", new byte[] {1, 2, 3}, MediaType.IMAGE_PNG_VALUE);

        server.verify();
    }

    @Test
    void signedUrlRequestAuthenticatesAndReturnsAbsoluteUrl() {
        server.expect(requestTo(BASE_URL + "/storage/v1/object/sign/" + BUCKET + "/users/123/avatar"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + SERVICE_ROLE_KEY))
                .andExpect(header("apikey", SERVICE_ROLE_KEY))
                .andExpect(content().json("{\"expiresIn\":300}"))
                .andRespond(withSuccess("{\"signedURL\":\"/object/sign/avatar?token=test\"}", MediaType.APPLICATION_JSON));

        assertThat(adapter.getAvatarSignedUrl("123"))
                .isEqualTo(BASE_URL + "/storage/v1/object/sign/avatar?token=test");

        server.verify();
    }
}
