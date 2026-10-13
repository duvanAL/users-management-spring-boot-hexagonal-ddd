package com.jcaa.usersmanagement.infrastructure.out.supabase;

import com.jcaa.usersmanagement.application.port.out.StoragePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
// Asegúrate de importar tu StoragePort
// import com.tuproyecto.application.port.out.StoragePort;

@Component
@ConditionalOnProperty(name = "app.storage.supabase.enabled", havingValue = "true")
public class SupabaseStorageAdapter implements StoragePort {

    // Inyectamos las variables de entorno configuradas
    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.storage.bucket}")
    private String bucketName;

    @Value("${supabase.service-role-key}")
    private String serviceRoleKey;

    private final RestTemplate restTemplate;

    public SupabaseStorageAdapter() {
        this.restTemplate = new RestTemplate();
    }

    @Override
    public void uploadAvatar(String userId, byte[] fileBytes, String contentType) {
        // 1. Construimos la ruta estable: users/{id}/avatar
        String url = supabaseUrl + "/storage/v1/object/" + bucketName + "/users/" + userId + "/avatar";

        // 2. Configuramos los headers con la Service Role Key para saltar las políticas RLS y mantener el bucket privado
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(serviceRoleKey);
        headers.setContentType(MediaType.parseMediaType(contentType));
        headers.set("x-upsert", "true");

        HttpEntity<byte[]> entity = new HttpEntity<>(fileBytes, headers);

        // 3. Enviamos la petición PUT para subir o reemplazar la foto previa
        restTemplate.exchange(url, HttpMethod.PUT, entity, String.class);
    }

    @Override
    public String getAvatarSignedUrl(String userId) {
        // 1. Endpoint específico de Supabase para firmar URLs (generar enlaces temporales)
        String url = supabaseUrl + "/storage/v1/object/sign/" + bucketName + "/users/" + userId + "/avatar";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(serviceRoleKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        // 2. El cuerpo de la petición define que el enlace expira en 300 segundos (5 minutos)
        String body = "{\"expiresIn\": 300}";
        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        try {
            // 3. Hacemos un POST para obtener la firma
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);

            // 4. Supabase devuelve un JSON con la propiedad "signedURL" que contiene la ruta relativa
            String signedPath = response.getBody().get("signedURL").toString();
            return supabaseUrl + "/storage/v1" + signedPath;

        } catch (Exception e) {
            // Si Supabase devuelve un error, lanzamos una excepción que el controlador capturará
            throw new RuntimeException("Error al obtener la imagen o avatar inexistente");
        }
    }
}
