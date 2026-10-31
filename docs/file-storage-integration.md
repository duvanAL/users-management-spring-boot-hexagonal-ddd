# Integración con File Storage Service

La rama `feat/file-storage-integration` añade un puerto síncrono independiente para archivos. Conserva los servicios Java existentes, sus eventos tipados y sus Transactional Outbox. El código del nuevo consumidor/adaptador B2 está en el repositorio independiente `file-storage-service` (TypeScript/NestJS).

## Archivos para revisar

- `application/port/in/ManageUserFilesUseCase.java`: operaciones de archivos y consulta de existencia.
- `application/port/out/FileStoragePort.java`: comandos/respuestas de transporte sin dependencias Spring.
- `application/service/ManageUserFilesService.java`: exige propietario o ADMIN y confirma existencia con GetUserByIdUseCase antes de llamar al puerto de archivos.
- `infrastructure/entrypoint/rest/controller/UserFilesController.java`: rutas multipart/JSON, contexto de usuario, propagación de status y validación de cabeceras/tamaño antes de leer bytes.
- `infrastructure/adapter/storage/HttpFileStorageAdapter.java`: HTTP con bearer token original, multipart, Idempotency-Key e If-Match. El token se obtiene en infraestructura y nunca se persiste en DTOs de aplicación ni logs. Los errores remotos se sanitizan; no se siguen redirecciones.
- `infrastructure/config/FileStorageConfiguration.java`: composición, feature flag, validación HTTPS y timeouts. El cliente no reintenta mutaciones automáticamente.
- `infrastructure/security/SecurityConfig.java`: solo las nuevas rutas de archivos/contexto admiten cualquier usuario autenticado; el caso de uso impone self/ADMIN. Las rutas generales de usuarios conservan roles ADMIN/REVIEWER y mutaciones ADMIN.
- `application.properties`: límites multipart configurables, conservando 2MB por defecto.

Las rutas son POST/GET `/api/users/{userId}/files`, GET/PUT/DELETE `/api/users/{userId}/files/{fileId}` y GET `/api/users/{userId}/files/{fileId}/download`. POST/PUT usan un único campo multipart `file`. Mutaciones requieren Idempotency-Key. PUT requiere If-Match con la revisión actual; el nuevo servicio valida el control de concurrencia. GET download devuelve JSON con una URL firmada temporal, no el binario. DELETE puede responder 202 cuando el borrado físico continúa mediante un job durable.

`GET /api/users/{userId}/storage-context` devuelve `{id,exists:true}` a self/ADMIN. File Storage usa exclusivamente este endpoint para confirmar existencia, con el JWT original; no llama de vuelta al proxy de archivos. Los 404 existentes de GetUserByIdUseCase se conservan. Los errores de cabeceras y parámetros de los nuevos endpoints devuelven 400; no se modificó el manejo global de errores de otras funcionalidades.

## Configuración

- APP_FILE_STORAGE_ENABLED=false por defecto. Sin habilitar, archivos responde 503 y el resto de User Service sigue funcionando.
- FILE_STORAGE_API_URL: URL HTTPS del nuevo microservicio, sin credenciales ni query.
- FILE_STORAGE_TIMEOUT_MS=15000; FILE_STORAGE_MAX_SIZE_BYTES=10485760.
- HTTP_MULTIPART_MAX_FILE_SIZE=10MB y HTTP_MULTIPART_MAX_REQUEST_SIZE=11MB para aceptar 10MiB por el proxy. Sin configurarlos se conserva el límite previo de 2MB. AvatarController mantiene su propia validación de 2MiB; no se reemplaza Supabase Storage.
- FILE_STORAGE_ALLOW_LOCAL_HTTP=true solo para pruebas locales explícitas. Producción requiere HTTPS.

Desplegar primero este código con el flag apagado para habilitar storage-context; provisionar esquema/ACL/bucket/secretos y arrancar File Storage; activar al final el flag y la URL. JWT_SECRET del nuevo servicio debe ser el mismo Base64 existente: el verificador deriva HS256/384/512 de su longitud como JJWT. No se inventó otro login, issuer, audiencia o secreto.

## Pruebas

`mvnw.cmd -B clean verify` ejecuta la regresión y nuevos tests de autorización/rutas. En File Storage, después de `npm ci`, `npm test` y levantar su compose de prueba, se puede ejecutar:

```text
node dist/test/run-integration.js --users-jar RUTA/target/users-management-2.1.0.jar
```

El runner levanta este JAR, PostgreSQL/Kafka/S3 simulados locales, genera credenciales efímeras, registra y activa usuarios de ensayo y obtiene un token real del login Java. Comprueba registro → outbox → Kafka → namespace, CRUD por el proxy, permisos, idempotencia, recuperación de fallos y descarga. No llama Render, Aiven, Supabase, Brevo ni Backblaze reales. Configuración y contratos detallados están en el README/OpenAPI del nuevo repositorio.
