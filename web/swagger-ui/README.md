# Swagger UI en Vercel

Esta carpeta publica una interfaz estática para la especificación OpenAPI que sigue sirviendo la API de Render.
No contiene la aplicación Spring ni credenciales.

## Configuración del proyecto de Vercel

- Importar el repositorio `duvanAL/users-management-spring-boot-hexagonal-ddd`.
- Root Directory: `web/swagger-ui`.
- Framework Preset: `Other`.
- Build Command: vacío (sitio estático).
- Output Directory: `public`.
- Production Branch: `deploy/render`.
- Mantener el plan Hobby/Free.

Las publicaciones de `develop` pueden usarse como previews; la rama `deploy/render` produce el sitio de producción.

## CORS de la API

La API debe permitir el origen estable de producción del proyecto Vercel en `CORS_ALLOWED_ORIGINS` en Render.
No uses `*`: el valor debe ser el origen exacto, por ejemplo `https://<dominio-del-proyecto>.vercel.app`.
Las variables se guardan en el panel de Render, nunca en Git.

Swagger permite ejecutar operaciones contra la API, incluidas creación, edición y eliminación de usuarios.
