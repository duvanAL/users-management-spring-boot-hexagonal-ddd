# Users Management - Spring Boot Hexagonal DDD

Aplicacion REST de gestion de usuarios basada en arquitectura hexagonal y DDD.
Este fork esta preparado para `duvanAL/users-management-spring-boot-hexagonal-ddd` y PostgreSQL remoto.

Este fork incorpora el historial actualizado del repositorio original (`upstream/main`) y conserva las adaptaciones de PostgreSQL, seguridad, Gmail API, Docker y despliegue web.

## Ramas y actualizacion del repositorio original

El remoto `origin` es el fork y `upstream` es el repositorio original
`arrietajohn/users-management-spring-boot-hexagonal-ddd`. La rama de trabajo
habitual es `develop`; `deploy/render` se reserva para los cambios que deben
publicarse en Render. Esta integración se prepara sobre una rama de sincronización
para conservar el despliegue y actualizar el código desde `upstream/main`.

La integración se valida primero en la rama de sincronización. Después de crear
su único commit, publícala en el fork y abre una PR hacia `develop`; una vez
validada, promueve `develop` a `deploy/render` mediante otra PR. No reemplaces
`deploy/render` directamente con el código original: contiene la configuración
activa de PostgreSQL, Render y Gmail. La [Guía de desarrollo y despliegue](docs/flujo-de-trabajo.md)
describe el flujo cotidiano.

## Fork y remotos

```powershell
git remote add origin https://github.com/duvanAL/users-management-spring-boot-hexagonal-ddd.git
git remote -v
```

`upstream` debe conservar `https://github.com/arrietajohn/users-management-spring-boot-hexagonal-ddd.git`.

## Configuracion PostgreSQL

No se guardan credenciales en el repositorio. Define estas variables en la terminal donde arranques la aplicacion:

```powershell
$env:DB_HOST = "host-remoto"
$env:DB_PORT = "5432"
$env:DB_NAME = "crud_usuarios"
$env:DB_USERNAME = "usuario"
$env:DB_PASSWORD = "contraseña"
$env:DB_SSLMODE = "require"
$env:APP_EMAIL_ENABLED = "false"
```

`DB_SSLMODE` acepta los modos soportados por el driver PostgreSQL, por ejemplo `disable`, `prefer`, `require`, `verify-ca` o `verify-full`.

Para desarrollo local, los valores predeterminados son `localhost:5432`, base
`crud_usuarios`, usuario `postgres` y `DB_SSLMODE=disable`. `DB_PASSWORD` debe
definirse: la aplicación rechaza valores vacíos antes de abrir conexiones.
Con Docker Compose, el puerto publicado por defecto es `5435`; definir
`DB_PORT=5435` al ejecutar la aplicación. En conexiones remotas, indicar el
modo SSL que requiera el proveedor.

La API permite ajustar el pool de conexiones mediante estas variables:

- `DB_POOL_MAX_SIZE`: máximo de conexiones; por defecto `5`, mínimo `1`.
- `DB_POOL_MIN_IDLE`: conexiones libres que se mantienen preparadas; por defecto
  `1`, entre `0` y el máximo configurado.
- `DB_CONNECTION_TIMEOUT_MS`: espera máxima para obtener una conexión del pool;
  por defecto `30000` milisegundos, mínimo `250`.

El puerto debe estar entre `1` y `65535`. Host, base, usuario y contraseña no
pueden estar vacíos. Los errores de validación identifican la variable que debe
corregirse sin incluir su valor. Las credenciales se pasan por separado al
driver y no forman parte de la URL JDBC.

Crear la base de datos, usando `psql` o la herramienta del proveedor:

```sql
CREATE DATABASE crud_usuarios;
```

Al iniciar la API, Spring ejecuta `src/main/resources/schema.sql` sobre la base
configurada. No es necesario ejecutar el archivo a mano. La base debe existir
y el usuario de conexión debe tener permisos para crear tablas e insertar datos.
Si falta el archivo o falla una sentencia SQL, la aplicación no termina de arrancar.

El script crea la tabla solo si no existe. En los reinicios conserva los datos.
No modifica tablas existentes: los cambios futuros de columnas requerirán
migraciones.

### Administrador inicial

La base nueva comienza sin usuarios. Para crear el primer administrador al
arrancar, configurar estas variables en la terminal o como secretos en Render:

- `SEED_ADMIN_ENABLED=true`: habilita la creación; por defecto es `false`.
- `SEED_ADMIN_EMAIL`: correo válido, con un máximo de 150 caracteres.
- `SEED_ADMIN_PASSWORD`: contraseña de al menos 8 caracteres y hasta 72 bytes
  en UTF-8, después de quitar espacios de los extremos, como en el login.

La creación se ejecuta después del esquema. Guarda un UUID nuevo, el nombre
`Administrador`, rol `ADMIN`, estado `ACTIVE` y un hash BCrypt con coste 12.
Si las credenciales no son válidas, el arranque falla sin mostrar sus valores.
Con la opción desactivada no se requieren ni se validan esas credenciales.

Si ya existe el mismo correo normalizado, no se modifica la cuenta: se conservan
su contraseña, rol y estado. Esta opción no restablece contraseñas ni convierte
usuarios existentes en administradores. Después del primer arranque, se puede
desactivar `SEED_ADMIN_ENABLED` y retirar las variables del correo y contraseña.

Las cuentas creadas antes de este cambio se conservan. Retirar el usuario fijo
del SQL no elimina una cuenta previamente creada ni cambia sus credenciales.

### PostgreSQL local con Docker Compose

Con Docker Desktop iniciado, levantar PostgreSQL 17:

```powershell
Copy-Item .env.example .env
docker compose up -d db
docker compose ps
```

Compose conserva los datos en el volumen `postgres_data` y espera a que
PostgreSQL responda a `pg_isready`. Los valores predeterminados de usuario y
contraseña son solo para desarrollo local; se pueden cambiar con las variables
`POSTGRES_USER`, `POSTGRES_PASSWORD` y `POSTGRES_DB`. No reutilizarlos en Render.
La base se publica únicamente en `localhost:5435` por defecto; cambiar ese puerto
con `DB_LOCAL_PORT` si ya está ocupado.

`.env.example` contiene únicamente valores locales de muestra. El archivo `.env`
no se incluye en Git ni en la imagen Docker. Compose lo lee para configurar la
base; para arrancar la aplicación directamente en PowerShell, también hay que
definir en esa terminal las variables `DB_*` de conexión.

Para conectar la aplicación ejecutada en el equipo, usar `DB_HOST=localhost`,
`DB_PORT=5435`, `DB_NAME=crud_usuarios`, `DB_USERNAME=postgres` y
`DB_PASSWORD=local_only_change_me`, o los valores personalizados elegidos.
Detener el contenedor con `docker compose down`; este comando conserva los datos.

## Arranque y smoke test

El proyecto compila para Java 17 y su Dockerfile usa Java 17. Para ejecutar las
verificaciones locales con el JDK 21 instalado en este equipo, en PowerShell:

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
& .\mvnw.cmd spring-boot:run
```

La API queda en `http://localhost:8080`. Los endpoints principales son:

```text
POST   /api/users/login
GET    /api/users
GET    /api/users/{id}
POST   /api/users
PUT    /api/users/{id}
DELETE /api/users/{id}
```

### Autenticacion y permisos

La API emite JWT mediante `POST /api/auth/login` con correo y contraseña; la
respuesta incluye el token Bearer. En Swagger UI usa **Authorize** y pega el
token (sin escribir `Bearer ` si la ventana ya indica ese prefijo). El token
vence según `JWT_EXPIRATION_SECONDS` (15 minutos por defecto). La clave de firma
se configura con `JWT_SECRET`; no hay una clave insegura predeterminada. Render
la genera con el Blueprint y no debe copiarse al repositorio.

El registro público `POST /api/users` siempre crea una cuenta `MEMBER`, aunque
el cuerpo incluya `"role": "ADMIN"` o cualquier otro rol. Solo una solicitud
autenticada con rol `ADMIN` puede indicar un rol al crear cuentas. `ADMIN` puede
listar, consultar, actualizar y eliminar usuarios; `REVIEWER` puede listar y
consultar; `MEMBER` no puede acceder a esas operaciones. El health check,
Swagger/OpenAPI, el registro y ambos endpoints de inicio de sesión permanecen
públicos. El endpoint anterior `POST /api/users/login` se conserva por
compatibilidad, y el endpoint recomendado para obtener un JWT es
`POST /api/auth/login`.

Una base recién creada no tiene administrador. Configura temporalmente
`SEED_ADMIN_ENABLED=true`, `SEED_ADMIN_EMAIL` y `SEED_ADMIN_PASSWORD` como
variables privadas para arrancar una única cuenta administrativa; tras el
primer arranque, desactiva el seed y retira el correo y la contraseña. No uses
credenciales de ejemplo ni envíes secretos en una PR.

Con `APP_EMAIL_ENABLED=false` (valor predeterminado), crear y actualizar usuarios no envían correos. Para enviar directamente desde una cuenta Gmail o Google Workspace se usa Gmail API por HTTPS (`APP_EMAIL_PROVIDER=gmail`), sin SMTP ni contraseña de aplicación. Configura `GMAIL_CLIENT_ID`, `GMAIL_CLIENT_SECRET`, `GMAIL_REFRESH_TOKEN`, `GMAIL_SENDER_ADDRESS` y, opcionalmente, `GMAIL_SENDER_NAME`. En Google Cloud, habilita Gmail API y autoriza el alcance `https://www.googleapis.com/auth/gmail.send`; el refresh token debe pertenecer a la cuenta indicada en `GMAIL_SENDER_ADDRESS`. Guarda estos valores únicamente como secretos en Render o en el `.env` local (que no se sube a Git). En Render el Blueprint deja el envío desactivado hasta que se hayan añadido las credenciales; después de configurarlas, establece `APP_EMAIL_ENABLED=true` y despliega. Nunca se incluye la contraseña del usuario en el correo de bienvenida.

## Despliegue en Render

La rama `deploy/render` contiene el Blueprint `render.yaml`, ya conectado al
fork. Este creó la API
[`users-management-api`](https://users-management-api-a4yf.onrender.com) y la base
PostgreSQL original `users-management-db` en Virginia. La API usa Java 17 dentro
de Docker, toma el puerto asignado por Render mediante `PORT` y aplica
`schema.sql` al arrancar. La base de Render se conservará durante el cambio a
Supabase para permitir una vuelta atrás; no se elimina como parte del despliegue.

El endpoint [`/actuator/health`](https://users-management-api-a4yf.onrender.com/actuator/health)
comprueba la aplicación y PostgreSQL sin exponer detalles. El último despliegue
respondió `UP` tanto en `status` como en `components.db.status`.

La configuración inicial usa planes gratuitos solo para pruebas; no es adecuada
para producción. Render suspende el servicio web tras 15 minutos sin tráfico y
despertarlo puede tardar alrededor de un minuto. La base gratuita tiene 1 GB,
vence a los 30 días, no incluye copias de seguridad y, después de vencer, solo
se puede actualizar durante un periodo de gracia de 14 días antes de que Render
la elimine. Respalda o migra los datos y elige un plan apropiado antes del
vencimiento. Consulta los [límites actuales del plan gratuito de Render](https://render.com/docs/free).

La migración objetivo usa Supabase Free para PostgreSQL y Vercel Hobby para
publicar solo la página estática de Swagger. La API continúa en Render. Antes de
activar la migración hay que crear el proyecto de Supabase, cargar sus datos si
la base actual ya contiene usuarios, configurar las variables secretas en
Render y validar `/actuator/health`. La guía está en
[`docs/despliegue-supabase-vercel.md`](docs/despliegue-supabase-vercel.md).

Si configuras el servicio manualmente en lugar de usar el Blueprint, usa:

```text
Build Command: docker build -t users-management-api .
Start Command: definido por el Dockerfile
Health Check Path: /actuator/health
```

## CI/CD con GitHub Actions y Render

El workflow `CI` se ejecuta en los pushes y pull requests de `develop` y
`deploy/render`. Configura Java 17, ejecuta `./mvnw -B clean verify` (incluidas
las pruebas PostgreSQL con Testcontainers) y publica el reporte JaCoCo. Si las
pruebas pasan, construye la imagen Docker, la ejecuta junto a un PostgreSQL
temporal y comprueba `/actuator/health`. Esta verificación no publica la imagen
en un registro.

Render despliega automáticamente los cambios de `deploy/render` una vez que
terminan correctamente las comprobaciones de GitHub. Para publicar una versión,
primero hacer los cambios y sus commits en `develop`, subirlos y esperar a que
`CI` pase. Después, abrir un pull request desde `develop` hacia `deploy/render`,
revisar que CI esté aprobado y fusionar el pull request. Usar una fusión que
conserve los commits individuales (no squash) para mantener el historial de
trabajo. Antes de fusionar, revisar cualquier diferencia en `render.yaml` y
conservar la configuración que ya está activa en `deploy/render`. Render
construye la versión fusionada y la activa cuando su comprobación
`/actuator/health` es satisfactoria.

Al terminar, revisar el despliegue en el dashboard de Render y comprobar
[`/actuator/health`](https://users-management-api-a4yf.onrender.com/actuator/health).
El workflow heredado que llamaba al Deploy Hook se eliminó de `main` en el
commit `8b57d40`. La configuración de despliegue automático está preparada;
falta confirmar el ciclo completo en la primera fusión de promoción desde
`develop` a `deploy/render`.
