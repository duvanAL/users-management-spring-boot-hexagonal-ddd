# Inventario de configuración de servicios

Este documento separa lo que la API ya configura de los servicios preparados en
las cuentas externas pero que aún requieren integración. No contiene valores de
conexión ni credenciales.

## Configuración que reconoce la API actual

La configuración está en `src/main/resources/application.properties`, `.env.example`
y `render.yaml`. La API usa JDBC con PostgreSQL; Aiven MySQL no es actualmente
compatible con este repositorio sin cambiar el driver y el adaptador de
persistencia.

| Variable | Uso | Tratamiento |
| --- | --- | --- |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` | Conexión PostgreSQL | `DB_PASSWORD` es secreto; los demás son configuración privada del servicio |
| `DB_SSLMODE` | TLS de PostgreSQL; Render/Supabase debe usar `require` | No es secreto |
| `DB_POOL_MAX_SIZE`, `DB_POOL_MIN_IDLE`, `DB_CONNECTION_TIMEOUT_MS` | Pool JDBC | No son secretos |
| `JWT_SECRET` | Firma de tokens | Secreto obligatorio; nunca incluir su valor en Git |
| `JWT_EXPIRATION_SECONDS` | Duración del JWT | No es secreto |
| `SEED_ADMIN_ENABLED` | Activación temporal del administrador inicial | No es secreto; mantener `false` después del seed |
| `SEED_ADMIN_EMAIL`, `SEED_ADMIN_PASSWORD` | Datos del administrador inicial | Tratar ambos como secretos; retirar después del primer arranque |
| `CORS_ALLOWED_ORIGINS` | Origen de Swagger UI | No es secreto |
| `PORT` | Puerto asignado por Render | Lo define Render; no fijarlo manualmente en producción |

### Correo: diferencia entre la rama actual y la rama del profesor

La rama de integración agrega Brevo REST como proveedor seleccionable y conserva
Gmail API y SMTP durante la transición. El correo está deshabilitado por defecto.

| Variable de la rama actual | Uso | Tratamiento |
| --- | --- | --- |
| `APP_EMAIL_ENABLED` | Habilita o deshabilita correo | No es secreto |
| `APP_EMAIL_PROVIDER` | `gmail`, `smtp` o `brevo` | No es secreto |
| `GMAIL_CLIENT_ID`, `GMAIL_CLIENT_SECRET`, `GMAIL_REFRESH_TOKEN` | OAuth de Gmail API | `CLIENT_SECRET` y `REFRESH_TOKEN` son secretos; proteger también el ID |
| `GMAIL_SENDER_ADDRESS`, `GMAIL_SENDER_NAME` | Remitente | No son secretos, pero son datos operativos |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_FROM_ADDRESS`, `SMTP_FROM_NAME` | Adaptador SMTP heredado/local | La contraseña es secreta; SMTP no es el proveedor previsto para Render |

El adaptador Brevo se configura con estas variables. Solo `BREVO_API_KEY` es
secreto; no debe registrarse ni compartirse:

| Variable de la rama Brevo | Uso | Tratamiento |
| --- | --- | --- |
| `BREVO_API_KEY` | Autenticación de la API REST | Secreto; nunca ponerlo en Git, logs o capturas |
| `BREVO_FROM_ADDRESS` | Remitente verificado en Brevo | Configuración privada |
| `BREVO_FROM_NAME` | Nombre del remitente | No es secreto |
| `BREVO_BASE_URL` | URL base de Brevo | No es secreto; valor por defecto en la rama: `https://api.brevo.com` |
| `BREVO_CONNECT_TIMEOUT_MS`, `BREVO_READ_TIMEOUT_MS` | Límites de espera HTTP en milisegundos | No son secretos |
| `BREVO_RETRY_MAX_ATTEMPTS`, `BREVO_RETRY_INITIAL_DELAY`, `BREVO_RETRY_MULTIPLIER` | Reintentos ante fallos transitorios | No son secretos |
| `BREVO_CIRCUIT_FAILURE_THRESHOLD`, `BREVO_CIRCUIT_WINDOW_SIZE`, `BREVO_CIRCUIT_MINIMUM_CALLS`, `BREVO_CIRCUIT_OPEN_DURATION` | Circuit breaker | No son secretos |

La API reconoce `BREVO_API_KEY`, `BREVO_FROM_ADDRESS`, `BREVO_FROM_NAME`,
`BREVO_BASE_URL`, `BREVO_CONNECT_TIMEOUT_MS` y `BREVO_READ_TIMEOUT_MS`. Los
valores de reintentos y circuit breaker se integrarán en un paso posterior; no
agregarlos a Render todavía.

## Servicios externos preparados, pendientes de integración

Los nombres siguientes son una propuesta para el diseño de la integración, no
variables que la API actual ya lea. No agregarlos todavía a Render ni a un
archivo de entorno hasta que la implementación y sus pruebas los confirmen.

### Aiven Kafka

Ya están creados los usuarios de servicio y las ACLs limitadas a los topics.
Las credenciales deben recuperarse y guardarse directamente en un gestor seguro;
no compartirlas por chat ni subirlas al repositorio.

| Variable propuesta | Uso | Tratamiento |
| --- | --- | --- |
| `KAFKA_BOOTSTRAP_SERVERS` | Host y puerto TLS de Aiven | Configuración privada |
| `KAFKA_SECURITY_PROTOCOL` | `SASL_SSL` | No es secreto |
| `KAFKA_SASL_MECHANISM` | `SCRAM-SHA-256` según el servicio | No es secreto; verificar en conexión |
| `KAFKA_USERNAME`, `KAFKA_PASSWORD` | Usuario de servicio correspondiente | Contraseña secreta |
| `KAFKA_USERS_GROUP_ID` | Grupo consumidor del API | Debe ser exactamente `users-api` para coincidir con su ACL |
| `KAFKA_NOTIFY_GROUP_ID` | Grupo consumidor de notificaciones | Debe ser exactamente `notify-service` para coincidir con su ACL |

Topics: `user.notification.requested`, `user.notification.result` y
`user.notification.dlq`. El API publica solicitudes y consume resultados; el
servicio de notificaciones consume solicitudes y publica resultados o mensajes
fallidos en la DLQ. La implementación debe decidir si serán dos procesos o un
servicio con ambos roles antes de asignar variables a Render.

### Aiven Valkey

El servicio está creado con conexión TLS (`rediss`). La aplicación todavía no
tiene caché distribuida integrada.

| Variable propuesta | Uso | Tratamiento |
| --- | --- | --- |
| `VALKEY_HOST`, `VALKEY_PORT`, `VALKEY_USERNAME` | Datos de conexión | Configuración privada |
| `VALKEY_PASSWORD` | Autenticación | Secreto |
| `VALKEY_SSL_ENABLED` | Debe estar activado para TLS | No es secreto |

Al implementar, confirmar compatibilidad de versión con Spring Boot y definir
qué datos pueden almacenarse en caché, caducidad e invalidación. No almacenar
contraseñas, tokens JWT ni secretos de usuario.

### Supabase Storage

El bucket `users-management-bucket` existe y es privado. Aún no hay flujo de
carga/descarga ni políticas de acceso definidos en la API.

| Variable propuesta | Uso | Tratamiento |
| --- | --- | --- |
| `SUPABASE_URL` | Proyecto de Supabase | Configuración privada |
| `SUPABASE_STORAGE_BUCKET` | `users-management-bucket` | No es secreto |
| `SUPABASE_SERVICE_ROLE_KEY` | Acceso de servidor al Storage | Secreto de alto privilegio; solo en Render/entorno local privado |

No hacer público el bucket ni crear políticas permisivas todavía. Primero se
deben definir tipos y tamaños de archivo, propietario del archivo, autorización
de cada endpoint y estrategia de borrado. La clave `service_role` nunca debe
exponerse a Swagger UI, Vercel ni al navegador.

## Reglas para guardar configuración

- Los valores reales van en secretos/variables privadas del proveedor o en el
  `.env` local, que está excluido de Git.
- `.env.example` solo contiene nombres y valores locales ficticios. No copiar
  contraseñas, tokens ni claves reales allí.
- GitHub Actions debe seguir usando bases efímeras de prueba; las pruebas no
  deben conectarse a Aiven, Supabase, Brevo ni Render.
- Agregar una variable a Render solo cuando el código de esa revisión ya la
  consuma. De lo contrario queda una configuración engañosa y difícil de
  mantener.

## Estado de este inventario

Brevo REST está disponible como adaptador seleccionable, pero no se habilita por
defecto ni se ha cambiado la configuración de Render. Kafka, Valkey y Storage
aún no están integrados. Se mantendrá un servicio a la vez y se actualizará este
inventario con cada cambio correspondiente.
