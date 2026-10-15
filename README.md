# lumorab

Lumora API foundation using Java 21, Spring Boot, Spring MVC, Validation, Security,
JPA, PostgreSQL, Flyway, Actuator, and springdoc OpenAPI. Requires Java 21 and
Docker with Compose for the local database. The Maven wrapper is included.

## Run locally (PowerShell)

```powershell
Copy-Item .env.example .env
docker compose up -d --wait
.\mvnw.cmd spring-boot:run
```

On macOS/Linux, use `cp .env.example .env` and `sh mvnw spring-boot:run`.
`JAVA_HOME` must point to a JDK 21 installation. Run commands from this repository
so Spring and Compose can load `.env`. Environment variables override that file.
The example credentials are for local development only; `.env` is Git-ignored.

- Health with database check: http://localhost:8080/api/health
- Actuator health: http://localhost:8080/actuator/health
- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs

The database is `homestay_booking`, UTF-8, UTC, and persisted in a named volume.
Its port is bound to loopback only. `docker compose down` preserves the data.
Changing example credentials after the first startup does not update existing
PostgreSQL users in the volume; change those users deliberately if needed.

## Checks and formatting

```powershell
.\mvnw.cmd verify
.\mvnw.cmd spotless:apply
# With the local PostgreSQL running and .env configured:
.\mvnw.cmd -Pintegration verify
```

Normal `verify` checks formatting, compiles, runs API/security/error tests and
packages an executable JAR. These tests need no database. The explicit integration
profile starts the complete application against your configured development
database, validates/reruns migrations, and tests health/OpenAPI over HTTP.
Use a disposable test database if you do not want migrations applied to local data.

## Configuration

| Variable | Purpose |
| --- | --- |
| `DB_HOST` | PostgreSQL host; defaults to localhost |
| `DB_PORT` | PostgreSQL port; defaults to 5432 |
| `DB_NAME` | Database name; defaults to homestay_booking |
| `DB_USER`, `DB_PASSWORD` | Required database credentials |
| `SERVER_PORT` | API port; defaults to 8080 |
| `CORS_ALLOWED_ORIGINS` | Comma-separated exact frontend origins; localhost:3000 by default |

No database passwords are embedded in application source/config. Every API error
uses `{ code, message, details, requestId }`; the ID is also returned in
`X-Request-Id`. Health returns HTTP 503 when the database is unavailable.

Security permits GET health and documentation endpoints. All other requests are
denied until Stage 1 adds Clerk JWT verification. A supplied bearer token is not
trusted yet. The stateless API does not use browser cookies/session login; CSRF
is disabled accordingly. Reassess CSRF if cookie authentication is introduced.
CORS allows only configured origins and exposes request IDs to the frontend.

## Structure

```text
com.lumora
  config/               Infrastructure, security, OpenAPI, auditing
  common/web/           Error contract and request correlation
  common/persistence/   UUID and audit timestamp base entity
  health/               Foundation API endpoint
src/main/resources/db/migration/  Immutable versioned Flyway migrations
docs/                   API contract and development conventions
```

Use domain packages for future features (`identity`, `property`, `booking`, etc.).
Within each domain use controller/service/repository/entity/dto as needed. Keep
business validation in services; frontend controls are not permission boundaries.
See `docs/foundation.md` for migration rules and the remaining phase gates.
