# Guía de desarrollo y despliegue

El objetivo es ejecutar la aplicación con PostgreSQL, preparar el entorno local
con Docker y desplegarla en Render. El trabajo se divide en cambios pequeños
para poder probarlos y seguir su evolución en Git.

## Estado inicial

El proyecto parte del [fork de duvanAL](https://github.com/duvanAL/users-management-spring-boot-hexagonal-ddd),
basado en el [repositorio de arrietajohn](https://github.com/arrietajohn/users-management-spring-boot-hexagonal-ddd).

El fork ya incluye PostgreSQL, configuración por variables de entorno, correo
opcional, un Dockerfile y archivos de CI/CD. Falta completar y probar esas
piezas, crear la base de datos y conectar el despliegue con las ramas del proyecto.

La revisión del 22 de septiembre de 2026 parte del commit `7068bad` del fork.
Frente al commit `697af7d` del original, hay siete commits propios y cinco
pendientes de incorporar. Estos últimos incluyen cambios de autenticación JWT
y configuración de Spring; su integración queda fuera de esta etapa.

## Cómo se usan las ramas

- `main` conserva la versión de partida del fork.
- `develop` reúne los cambios de desarrollo y sus pruebas.
- `deploy/render` contendrá la versión publicada en Render. Se creará desde
  `develop` al llegar al paso 15.

Los cambios se preparan en `develop`. Cuando estén probados y listos para
publicar, se integran en `deploy/render`, conservando los commits individuales.
Así se puede seguir desarrollando sin actualizar la aplicación publicada con
cada cambio.

El remoto `origin` apunta al fork personal y recibe los cambios. El remoto
`upstream` apunta al proyecto original y permite consultar sus actualizaciones.

La configuración de CI/CD existente todavía usa `main` y un Deploy Hook.
Hasta adaptarla, los cambios de este trabajo se publican únicamente en `develop`.

## Cómo guardar cada avance

Cada commit debe resolver una tarea concreta: configuración, base de datos,
pruebas, Docker o despliegue. Antes de guardarlo, revisar los archivos modificados
y ejecutar las pruebas que correspondan. Publicar ese avance antes de empezar
la siguiente tarea.

Para este primer cambio de documentación, desde la carpeta del proyecto:

```powershell
git status --short --branch
git diff
git add README.md docs/flujo-de-trabajo.md
git commit -m "chore: document branch workflow and baseline"
git push -u origin develop
```

En los siguientes commits, seleccionar los archivos de la tarea con `git add`,
escribir un mensaje que describa el cambio y publicar con `git push origin develop`.

La entrega se organizará en al menos 15 commits nuevos con cambios concretos,
sin contar la preparación del repositorio. Los pasos siguientes sirven como
guía; las partes ya implementadas se revisarán y completarán según lo necesario.

## Orden de implementación

1. **Documentación inicial.** Explicar las ramas, el estado del proyecto y cómo
   compilar y ejecutar las pruebas.
2. **Driver PostgreSQL.** Revisar la dependencia existente y su compatibilidad
   con el proyecto.
3. **Conexión a la base de datos.** Completar la configuración por variables de
   entorno para desarrollo local y Render.
4. **Creación de tablas.** Comprobar que el esquema funciona en una base vacía
   y que reiniciar la aplicación no elimina ni duplica datos.
5. **Administrador inicial.** Reemplazar el usuario fijo del SQL por una creación
   opcional mediante `SEED_ADMIN_ENABLED`, `SEED_ADMIN_EMAIL` y
   `SEED_ADMIN_PASSWORD`.
6. **Correo opcional.** Verificar que la aplicación funciona sin credenciales
   SMTP cuando `APP_EMAIL_ENABLED=false`.
7. **Base local con Docker.** Añadir PostgreSQL 17 a Docker Compose, con
   almacenamiento persistente y comprobación de disponibilidad.
8. **Pruebas de base de datos.** Probar el esquema y las operaciones de
   persistencia contra PostgreSQL real.
9. **Pruebas de la API.** Cubrir las operaciones de usuarios, las validaciones
   y las respuestas de error.
10. **Estado de la aplicación.** Añadir `/actuator/health` para comprobar también
    la conexión a la base. El `/health` actual solo devuelve `UP`.
11. **Imagen Docker.** Revisar el Dockerfile existente y comprobar que la
    aplicación arranca con la configuración de despliegue.
12. **Archivos de entorno.** Completar `.dockerignore` y `.env.example`, usando
    valores de ejemplo y sin guardar contraseñas reales.
13. **Pruebas automáticas en GitHub.** Adaptar el workflow de Maven a `develop`
    y `deploy/render`, incluyendo las pruebas de integración.
14. **Verificación de Docker en GitHub.** Comprobar la construcción y el
    funcionamiento del contenedor dentro de CI.
15. **Despliegue en Render.** Crear `deploy/render` y completar `render.yaml`
    con el servicio web, PostgreSQL 17, variables y comprobación de salud.
    Coordinar el despliegue automático con CI y retirar el hook anterior
    para evitar despliegues duplicados.
16. **Guía de publicación.** Documentar las variables necesarias y cómo pasar
    una versión probada de `develop` a `deploy/render`.
17. **Prueba del servicio publicado.** Comprobar la URL de Render y una operación
    de la API que utilice la base de datos.
18. **Documentación de cierre.** Registrar la URL, los resultados de las pruebas
    y el mantenimiento necesario para el plan gratuito.

## Compilación y pruebas

El proyecto usa Spring Boot 3.3.5 y compila para Java 17. El wrapper incluido
descarga Maven 3.9.6 si hace falta. Java 11 no permite compilar este proyecto.

En Windows, con el JDK 21 instalado en este equipo:

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\mvnw.cmd -version
.\mvnw.cmd -B clean verify
```

La ruta de `JAVA_HOME` debe corresponder al JDK instalado en cada equipo.
En Linux o CI, con JDK 17 configurado:

```sh
./mvnw -B clean verify
```

Al finalizar, se generan:

- Aplicación: `target/users-management-2.1.0.jar`.
- Resultados de pruebas: `target/surefire-reports`.
- Reporte de cobertura: `target/site/jacoco/index.html`.

La comprobación inicial terminó correctamente: **194 pruebas, sin fallos,
errores ni pruebas omitidas**, usando JDK 21.0.8 y compilación para Java 17.
Queda por verificar la ejecución con Java 17 y PostgreSQL real, ya que las
pruebas actuales de persistencia simulan las conexiones JDBC.

La base remota y el servicio en Render se comprobarán durante el despliegue.
Antes de esa etapa debe retirarse el administrador con contraseña fija que
todavía contiene el esquema SQL.
