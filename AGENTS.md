# Users Management — Spring Boot REST API

## Project

Java 17, Spring Boot 3.3.5, hexagonal architecture and DDD. The active entry point is the REST API. Spring is the composition root; the desktop CLI is an inactive adapter.

## Runtime and deployment

- Persistence is raw JDBC against PostgreSQL (`UserRepositoryPostgres`).
- Local PostgreSQL runs with `compose.yaml`; Render runs the Docker image from `deploy/render` and connects to Supabase PostgreSQL.
- Email is sent through Gmail API over HTTPS when enabled; SMTP is not suitable for the Render deployment.
- Vercel hosts only the static Swagger UI. The API and OpenAPI document are served by Render.
- GitHub Actions runs `./mvnw -B clean verify`, including PostgreSQL integration tests with Testcontainers, then builds and smoke-tests the Docker image.

## Architecture and conventions

The dependency direction is domain ← application ← infrastructure. Do not add infrastructure imports to domain or application code. Keep DTOs, commands and queries as records; validate inputs; use domain exception factories; do not log personal data or secrets; avoid wildcard imports and unused imports.

Use the Spring application context and existing `@Configuration` classes for runtime wiring. Do not add another manual composition root for the REST API.

## Security

- JWT signing keys must come from `JWT_SECRET`; never add a default or commit a real secret.
- Anonymous account registration must always assign `MEMBER`, regardless of a submitted role. Assigning privileged roles requires an authenticated administrator.
- Keep `/actuator/health`, login, registration and OpenAPI documentation reachable as required; protect user reads and mutations by role as defined in `SecurityConfig`.
- Keep bearer-token support visible in the generated OpenAPI document so Swagger can authorize requests.

## Verification

Run `./mvnw -B clean verify` (Windows: `mvnw.cmd -B clean verify`). Tests must not depend on live Render, Supabase, Gmail or real user credentials.
