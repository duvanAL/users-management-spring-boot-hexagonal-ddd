# Users Management - Spring Boot Hexagonal DDD

Aplicacion REST de gestion de usuarios basada en arquitectura hexagonal y DDD.
Este fork esta preparado para `duvanAL/users-management-spring-boot-hexagonal-ddd` y PostgreSQL remoto.

## Desarrollo incremental

Los cambios se desarrollan y prueban en `develop`, organizados en commits
pequenos. La [Guía de desarrollo y despliegue](docs/flujo-de-trabajo.md)
explica las ramas, el estado inicial del proyecto y los pasos para publicarlo
en Render.

`main` conserva el estado existente del fork. La rama `deploy/render` se creara
desde `develop` en el punto 15, antes de conectar el despliegue. Las secciones de
despliegue y CI/CD de este README describen la configuracion heredada: actualmente
usa `main` y un Deploy Hook; su adaptacion a `deploy/render` y auto-deploy esta
pendiente. La presencia de esos archivos no confirma un servicio desplegado.

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
En conexiones remotas, indicar el modo SSL que requiera el proveedor.

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

Con `APP_EMAIL_ENABLED=false` (valor predeterminado), crear y actualizar usuarios no intentan conectarse a SMTP y no requieren credenciales de correo. Para activar correo real, define `APP_EMAIL_ENABLED=true` y las variables `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_FROM_ADDRESS` y `SMTP_FROM_NAME`.

## Despliegue en Render

El repositorio incluye `Dockerfile` y `render.yaml`. En Render puedes crear el servicio desde **New + > Blueprint** y seleccionar este repositorio. El servicio usa Java 17 dentro de Docker, toma el puerto asignado por Render mediante `PORT` y expone `/health` como comprobacion de salud.

En la configuracion del servicio define los valores reales de `DB_HOST`, `DB_NAME`, `DB_USERNAME` y `DB_PASSWORD`. `render.yaml` deja esas variables como secretas y establece `DB_SSLMODE=require` para PostgreSQL remoto. La base PostgreSQL debe existir antes del primer despliegue; la API aplica `schema.sql` automáticamente al arrancar.

Si configuras el servicio manualmente en lugar de usar el Blueprint, usa:

```text
Build Command: docker build -t users-management-api .
Start Command: definido por el Dockerfile
Health Check Path: /health
```

## CI/CD con GitHub Actions y Render

El workflow `CI` se ejecuta en cada pull request y en cada push a `main`. Ejecuta todas las pruebas, genera el reporte JaCoCo y construye la imagen Docker.

El workflow `Deploy to Render` se ejecuta solamente despues de un CI exitoso en `main`. Para activarlo:

1. En Render crea el Web Service desde `render.yaml` y genera un **Deploy Hook**.
2. En GitHub abre `Settings > Secrets and variables > Actions > New repository secret`.
3. Crea el secreto `RENDER_DEPLOY_HOOK_URL` con la URL del Deploy Hook de Render.

El hook no se guarda en el repositorio. Las credenciales PostgreSQL siguen configurandose exclusivamente como variables secretas del servicio en Render.
