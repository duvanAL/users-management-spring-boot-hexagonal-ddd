# Supabase y Vercel

## Arquitectura prevista

- Spring Boot y Docker permanecen en Render.
- PostgreSQL se mueve a un proyecto Supabase del plan Free.
- Swagger UI se publica como sitio estático en Vercel Hobby; no se despliega una
  segunda copia de la API.
- La base PostgreSQL de Render se conserva hasta validar la nueva conexión y los
  datos. No borrarla durante el cambio.

## Supabase

Crear la organización y el proyecto con el plan Free, en `East US (North
Virginia)`, junto a la API. La aplicación debe usar el **Session pooler** de
Supabase en el puerto `5432`: funciona sobre IPv4 y permite las conexiones
persistentes/preparadas que usa Spring JDBC. No usar el Transaction pooler en el
puerto `6543` para el pool de la aplicación.

Configurar en el servicio Render `users-management-api`:

| Variable | Valor |
| --- | --- |
| `DB_HOST` | Host del Session pooler mostrado por Supabase |
| `DB_PORT` | `5432` |
| `DB_NAME` | `postgres` |
| `DB_USERNAME` | Usuario del Session pooler, normalmente `postgres.<project-ref>` |
| `DB_PASSWORD` | Contraseña del proyecto, solo en Render y Supabase; nunca en Git |
| `DB_SSLMODE` | `require` |

La app crea la tabla `users` usando `schema.sql` al iniciar. Antes del cambio,
comprobar si la base de Render tiene registros. Si contiene datos que se deben
conservar, exportarlos y restaurarlos en Supabase **antes** de cambiar Render.
No exponer el volcado ni las credenciales en el repositorio o en logs.

Supabase Free ofrece 500 MB de base de datos, puede pausar proyectos tras una
semana con poca actividad y no incluye backups automáticos. Revisar su
[política de pausas](https://supabase.com/docs/guides/platform/free-project-pausing)
y [límites actuales](https://supabase.com/pricing); no es una garantía de
disponibilidad continua.

## CORS de Swagger

El sitio de Vercel debe poder solicitar la especificación OpenAPI y ejecutar
operaciones en la API. Definir `CORS_ALLOWED_ORIGINS` en Render con el origen
exacto que asigne Vercel, por ejemplo `https://<proyecto>.vercel.app`. No usar
`*`. Esta configuración habilita llamadas desde ese sitio, pero no autentica ni
protege por sí misma las operaciones de la API.

## Vercel

Importar `duvanAL/users-management-spring-boot-hexagonal-ddd` y configurar:

- Plan Hobby/Free.
- Root Directory: `web/swagger-ui`.
- Framework Preset: `Other`.
- Build Command: vacío.
- Output Directory: `public`.
- Production Branch: `deploy/render`.
- `develop` queda para previews; al promover a `deploy/render`, se actualiza el
  dominio público.

La interfaz Swagger carga `https://users-management-api-a4yf.onrender.com/v3/api-docs`.
Render seguirá durmiendo el servicio gratuito después de inactividad; la página
puede abrir antes de que la primera llamada despierte la API.

## Comprobación de corte

1. Confirmar que los datos requeridos están presentes en Supabase.
2. Cambiar las variables de conexión de Render, guardar y esperar el despliegue.
3. Verificar que `/actuator/health` muestre `status=UP` y `components.db.status=UP`.
4. Probar lecturas y operaciones de prueba desde Swagger sin borrar datos reales.
5. Conservar PostgreSQL de Render hasta completar las verificaciones y decidir
   manualmente si ya se puede retirar.
