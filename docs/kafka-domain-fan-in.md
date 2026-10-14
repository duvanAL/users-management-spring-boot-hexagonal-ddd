# Transición a tres servicios

Código publicado no equivale a desplegado. Estos pasos se ejecutan después de crear los recursos Azure.

1. Integrar la PR de eventos de dominio en la rama deploy/render de usuarios.
   El valor predeterminado APP_DOMAIN_EVENTS_ENABLED=false preserva el flujo actual.
2. Aplicar en PostgreSQL el SQL V20261013_01__create_domain_event_outbox.sql del repo de usuarios.
   Aplicar también schema.sql de cuentas con sus credenciales/schema propio.
3. Crear/configurar la identidad Kafka accounts-service y verificar las ACL mínimas.
   Conservar las identidades users-api y notify-service y la CA TLS Aiven.
4. Desplegar notificaciones separado. Puede consumir el formato legado, con mismo grupo notify-service.
5. En la API de usuarios fijar APP_KAFKA_IN_PROCESS_WORKER_ENABLED=false, manteniendo
   APP_DOMAIN_EVENTS_ENABLED=false. Fijar también APP_EMAIL_ENABLED=false: los eventos
   siguen habilitados con Kafka y Brevo ahora pertenece al servicio separado.
   Esperar despliegue completado y salida del proceso anterior.
6. Solo entonces activar APP_DOMAIN_EVENTS_ENABLED=true. APP_KAFKA_ENABLED debe seguir true.
   La API encola eventos de dominio y deja de generar un correo legado para esa misma operación.
7. Desplegar cuentas con PostgreSQL, USERS_API_URL y JWT_SECRET compartido con usuarios.
   Mantener worker de notificaciones activo (mínimo 1 réplica) y comprobar que no compite con un worker viejo.
8. Probar registro usuario -> correo; creación cuenta -> segundo correo, log de ambos eventName,
   resultados Kafka, outbox vacío y ausencia de contraseñas/tokens en mensajes.
9. Probar desconexión/reconexión en un entorno de prueba. Kafka retiene registros durante 72h
   y confirma mediante offsets; no reproduce el borrado inmediato de RabbitMQ.

Para revertir los productores, desactivar APP_DOMAIN_EVENTS_ENABLED.
El consumidor nuevo acepta ambos formatos, así que puede seguir atendiendo notificaciones legadas.
No borrar tablas/topics mientras haya eventos pendientes.

No se configura Azure ni se amplían ACLs desde estos archivos.
No se requiere eliminar el Kafka o la infraestructura que ya funciona.

# Contrato Kafka v1

Dos productores independientes convergen en un consumidor:
- users-management: user.created y user.updated.
- users-accounts-service: account.created.
- users-notification-service: transforma esos eventos en correo Brevo.

Se reutiliza el topic existente `user.notification.requested` como entrada compartida de fan-in.
Aunque su nombre conserva el prefijo user, recibe eventos de ambos servicios.
No se requieren topics nuevos. Los otros topics siguen siendo `user.notification.result` y `user.notification.dlq`.

El cuerpo es JSON UTF-8 y la key es eventId. Se ignoran los headers de tipos Java:
ningún servicio depende del classpath o del paquete Java del otro.
Schema y ejemplos se incluyen en esta carpeta. eventId permanece estable en cada reintento.
Los eventos nunca contienen contraseñas, tokens JWT ni claves.

Un resultado conserva el formato anterior:
`{"schemaVersion":1,"notificationId":"UUID del evento","status":"SENT","processedAt":"fecha ISO","failureCode":null}`.
Para fallo terminal se publica FAILED y un código, junto a metadatos en DLQ.
La DLQ nueva no copia el correo ni el HTML original.

Kafka retiene los registros según su retención (actualmente 72h). Confirmar el procesamiento
avanza el offset del grupo; no elimina el registro inmediatamente.
El outbox conserva eventos pendientes hasta que Kafka confirma su publicación.
Esto no promete entrega exactamente una vez. Brevo recibe eventId como idempotencyKey;
su deduplicación tiene una ventana limitada y no sustituye un registro permanente de entregas.

Para cambiar el contrato, actualizar las copias del schema, fixtures y record en los tres repositorios.
No introducir campos incompatibles sin versionar. No se publica una biblioteca Maven privada.
