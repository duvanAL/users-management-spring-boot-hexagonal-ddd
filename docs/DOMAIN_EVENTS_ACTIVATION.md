# Activación controlada: API de usuarios como productor de dominio

Esta rama prepara el código/configuración; no afirma que esté desplegada. No cambiar secretos ni
activar flags en nube antes de revisar los commits, publicar el worker compatible y aplicar el DDL.

## Orden de despliegue después de aprobar los cambios

1. Publicar y desplegar primero el worker que separa listener/use case y reconoce `user.deleted`.
   Mantiene compatibilidad con solicitudes legacy pendientes.
2. En la misma base y esquema usados por la API de usuarios, aplicar con acceso DDL autorizado
   `src/main/resources/db/migration/V20261013_01__create_domain_event_outbox.sql`.
   Esta carpeta no implica Flyway automático: el proyecto no declara Flyway. El archivo es SQL
   versionado a ejecutar de forma controlada; conservar las tablas/outbox antiguas.
3. Verificar permisos del rol JDBC de usuarios: SELECT, INSERT, UPDATE y DELETE en
   `domain_event_outbox`. No conceder acceso a tablas de cuentas para esta migración.
4. Integrar esta rama mediante PR hacia `deploy/render` y esperar Verify. No desplegar una rama
   antigua ni cambiar secretos de producción para resolver un error de arranque.
5. Configurar `APP_RUNTIME_ROLE=api`, `APP_KAFKA_ENABLED=true`,
   `APP_DOMAIN_EVENTS_ENABLED=true`, `APP_KAFKA_IN_PROCESS_WORKER_ENABLED=false`,
   `APP_EMAIL_ENABLED=false`, `DB_SCHEMA_INIT_MODE=never`.
   Conservar endpoint, principal y contraseña existentes; SASL_SSL, SCRAM-SHA-256 y CA PEM válidos.
   El blueprint declara los flags; un servicio existente puede requerir sincronizar/revisarlos
   manualmente. Un commit no cambia automáticamente el panel de Render.
6. Crear/actualizar solo una identidad de demostración autorizada y correlacionar el eventId en
   relay y worker; verificar entrega en Brevo. El retorno 201/200 no demuestra entrega de correo.
7. Si se prueba DELETE de una identidad desechable, el evento lleva solo userId. El worker debe
   registrar la omisión intencional sin llamar a Brevo, sin SENT y sin DLQ para el evento válido.

## Garantías y reversión

`EmailNotificationService` retorna inmediatamente tras encolar el evento tipado: no genera
también un correo legacy para la misma operación. Cuenta/usuario y su intención de publicación
usan una transacción local, no una transacción distribuida Kafka/DB/correo. La entrega es al menos
una vez. El borrado de usuario ahora guarda su evento en la misma transacción local.

Ante error detener la activación y diagnosticar DB/ACL/configuración; no repetir altas válidas.
Para volver al productor legacy se puede desactivar `APP_DOMAIN_EVENTS_ENABLED`, manteniendo el
worker compatible. Los pendientes tipados quedan retenidos hasta reactivar su relay: no borrar
la tabla ni asumir que el rollback los publicó. No desactivar la validación TLS.
