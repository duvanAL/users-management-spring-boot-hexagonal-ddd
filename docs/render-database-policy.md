# PostgreSQL de producción fuera de Render

Render administra exclusivamente la API. PostgreSQL está en Supabase y sus parámetros `DB_*` se configuran como variables externas.

No declarar `databases` ni usar `fromDatabase` en `render.yaml`. `RenderBlueprintPolicyTest` valida ambas restricciones en CI para evitar que una sincronización vuelva a crear la base gratuita expirada.

El Blueprint activo usa `deploy/render`. Las ramas `main` y `develop` también deben conservar esta política. Las ramas y carpetas históricas no son fuentes de despliegue; cualquier integración hacia las ramas activas debe pasar su CI.

Retirar una definición no borra necesariamente un recurso existente. La base antigua `users-management-db` se desvincula del Blueprint y su eliminación permanente se confirma por separado. Verificar antes que la API siga apuntando a Supabase.
