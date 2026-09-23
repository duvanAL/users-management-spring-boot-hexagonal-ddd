-- =============================================
-- Esquema PostgreSQL para la base de datos crud_usuarios.
-- Crear la base previamente (por ejemplo: CREATE DATABASE crud_usuarios).
-- Spring ejecuta este archivo al iniciar la API; tambien puede aplicarse con psql.
-- IF NOT EXISTS conserva las tablas. La creacion del administrador es opcional
-- y se configura por variables de entorno, fuera del esquema.
-- Este script no migra estructuras anteriores: futuros cambios de columnas
-- requieren una migracion explicita, no basta con editar CREATE TABLE.
-- =============================================

CREATE TABLE IF NOT EXISTS users (
    id          VARCHAR(36)  NOT NULL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    email       VARCHAR(150) NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,
    role        VARCHAR(20) NOT NULL CHECK (role IN ('ADMIN', 'MEMBER', 'REVIEWER')),
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                CHECK (status IN ('ACTIVE', 'INACTIVE', 'PENDING', 'BLOCKED')),
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
