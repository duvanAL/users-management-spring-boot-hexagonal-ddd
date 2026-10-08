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
| `DB_SCHEMA_INIT_MODE` | Controla la ejecución de `schema.sql`; local usa `always`, Render usa `never` | No es secreto; en producción requiere que el esquema ya esté aplicado |
| `JWT_SECRET` | Firma de tokens | Secreto obligatorio; nunca incluir su valor en Git |
| `JWT_EXPIRATION_SECONDS` | Duración del JWT | No es secreto |
| `SEED_ADMIN_ENABLED` | Activación temporal del administrador inicial | No es secreto; mantener `false` después del seed |
| `SEED_ADMIN_EMAIL`, `SEED_ADMIN_PASSWORD` | Datos del administrador inicial | Tratar ambos como secretos; retirar después del primer arranque |
| `CORS_ALLOWED_ORIGINS` | Origen de Swagger UI | No es secreto |
| `PORT` | Puerto asignado por Render | Lo define Render; no fijarlo manualmente en producción |

Render usa `/health` como health check de liveness para no marcar el proceso como
caído ante una indisponibilidad temporal de PostgreSQL; el estado de la base se
consulta en `/actuator/health`. Hikari permite iniciar sin conexión inicial y
crea conexiones bajo demanda (`DB_POOL_MIN_IDLE=0`), por lo que puede reconectar
cuando PostgreSQL responda. En Render, `DB_SCHEMA_INIT_MODE=never` evita que el
arranque dependa de ejecutar el esquema sobre la base remota: el esquema debe
estar aplicado previamente y cualquier cambio futuro debe realizarse mediante
una migración explícita. En local se mantiene `always`.

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
| `BREVO_RETRY_MAX_ATTEMPTS`, `BREVO_RETRY_INITIAL_DELAY_MS`, `BREVO_RETRY_MULTIPLIER` | Reintentos ante fallos transitorios | No son secretos |
| `BREVO_CIRCUIT_FAILURE_THRESHOLD`, `BREVO_CIRCUIT_WINDOW_SIZE`, `BREVO_CIRCUIT_MINIMUM_CALLS`, `BREVO_CIRCUIT_OPEN_DURATION_MS` | Circuit breaker | No son secretos |

La API ya consume las variables anteriores. Las opciones de reintento tienen
valores predeterminados seguros: 3 intentos como máximo (incluyendo el primero),
espera exponencial de 500 ms y multiplicador 2. El circuit breaker usa una
ventana de 10 llamadas, requiere al menos 5 para calcular fallos, abre al alcanzar
el 50 % y permanece abierto 30 segundos. Se pueden sobreescribir desde el entorno;
no es necesario añadirlas a Render mientras esos valores predeterminados sean
adecuados. No activar `APP_EMAIL_ENABLED` ni cambiar el proveedor de producción
como parte de esta integración.

El reintento aplica a fallos transitorios (`408`, `429`, `5xx` y errores de
conexión/timeout), no a errores permanentes como `400` o `401`. Como el envío es
una operación HTTP `POST`, si Brevo acepta el correo pero la respuesta se pierde
por un timeout, un reintento podría producir un duplicado; el envío exactamente
una vez no se puede garantizar solo con el cliente. El circuit breaker evalúa el
resultado final después de agotar los reintentos y bloquea temporalmente nuevas
llamadas cuando el proveedor presenta fallos sostenidos.

## Servicios externos preparados, pendientes de integración

Los nombres siguientes son una propuesta para el diseño de la integración, no
variables que la API actual ya lea. No agregarlos todavía a Render ni a un
archivo de entorno hasta que la implementación y sus pruebas los confirmen.

### Aiven Kafka

La API y el worker usan procesos y credenciales separados. Los usuarios de
servicio y las ACLs deben estar limitados a los topics que les corresponden.
No compartir credenciales por chat ni subirlas al repositorio.

| Variable | Uso | Tratamiento |
| --- | --- | --- |
| `APP_KAFKA_ENABLED` | Activa productores y consumidores Kafka | No es secreto; por defecto `false` |
| `APP_RUNTIME_ROLE` | `api` o `notification-worker` | No es secreto |
| `KAFKA_BOOTSTRAP_SERVERS` | Host y puerto TLS de Aiven, sin `https://` | Configuración privada |
| `KAFKA_SECURITY_PROTOCOL` | `SASL_SSL` | No es secreto |
| `KAFKA_SASL_MECHANISM` | `SCRAM-SHA-256` | No es secreto; debe coincidir con Aiven |
| `KAFKA_USERNAME`, `KAFKA_PASSWORD` | Credenciales del proceso desplegado | La contraseña es secreta; API usa `users-api` y worker usa `notify-service` |
| `KAFKA_USERS_GROUP_ID` | Grupo consumidor de resultados de la API | Mantener `users-api` según su ACL |
| `KAFKA_NOTIFY_GROUP_ID` | Grupo consumidor de solicitudes del worker | Mantener `notify-service` según su ACL |
| `KAFKA_TOPIC_NOTIFICATION_REQUESTED`, `KAFKA_TOPIC_NOTIFICATION_RESULT`, `KAFKA_TOPIC_NOTIFICATION_DLQ` | Topics de solicitud, resultado y mensajes fallidos | No secretos; deben coincidir exactamente con Aiven |
| `KAFKA_SEND_TIMEOUT_MS` | Máximo de espera por confirmación del broker | No es secreto |
| `KAFKA_OUTBOX_POLL_INTERVAL_MS` | Intervalo de sondeo del outbox en la API | No es secreto; 5000 ms por defecto |
| `KAFKA_LISTENER_AUTO_STARTUP` | Permite pausar el consumo sin deshabilitar la configuración | No es secreto; por defecto `true` |

Flujo: el API envía `user.notification.requested` y consume
`user.notification.result`; el worker consume solicitudes, envía correos mediante
el proveedor configurado, publica resultados y coloca fallos de entrega en
`user.notification.dlq`. En la API, `APP_KAFKA_ENABLED=true` y
`APP_RUNTIME_ROLE=api`; en el worker, `APP_KAFKA_ENABLED=true`,
`APP_RUNTIME_ROLE=notification-worker`, `APP_EMAIL_ENABLED=true` y
`APP_EMAIL_PROVIDER=brevo`. El worker arranca un contexto reducido y no requiere
conectarse a PostgreSQL. No configurar `KAFKA_USERNAME`/`KAFKA_PASSWORD` iguales
entre ambos servicios.

La API persiste ahora cada solicitud de correo en `email_notification_outbox`
dentro de la misma transacción PostgreSQL que crea o actualiza al usuario. Un
proceso programado publica una solicitud pendiente por ciclo; si Kafka no
confirma, conserva la fila y reintenta con espera exponencial acotada (5 s hasta
15 min). Tras confirmación del broker elimina la fila. `KAFKA_OUTBOX_POLL_INTERVAL_MS`
controla el intervalo de sondeo (5 s por defecto). La migración está en
`src/main/resources/db/migration/V20261008_01__create_email_notification_outbox.sql`.
Como Render usa `DB_SCHEMA_INIT_MODE=never`, debe aplicarse manualmente en
Supabase antes de desplegar una revisión con Kafka activado.

Los mensajes de solicitud contienen correo, nombre y contenido HTML, por lo que
los topics deben permanecer privados y protegidos por TLS/ACLs; la retención de
72 horas limita el tiempo de exposición. El envío es de tipo *at least once*: si
Brevo acepta el mensaje pero el worker falla antes de publicar el resultado, una
redelivery puede producir un correo duplicado. La publicación del API es *at
least once*: si el broker acepta un mensaje pero se pierde la confirmación, el
outbox puede publicarlo nuevamente con el mismo `notification_id`. El worker
debe tolerar duplicados. El outbox guarda correo, nombre y contenido HTML; las
filas se eliminan tras confirmación Kafka y los reintentos pendientes deben
monitorearse y purgarse conforme a una política de retención adecuada.

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

Brevo REST está disponible como adaptador seleccionable, con reintentos acotados
y circuit breaker. Kafka tiene productores/consumidores para la API y un worker
de notificaciones en un proceso separado; ambos siguen desactivados por defecto
y la configuración de Render no se ha cambiado. Valkey y Storage aún no están
integrados. Se mantendrá un servicio a la vez y se actualizará este inventario
con cada cambio correspondiente.
