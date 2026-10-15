# Stage 0 foundation

## Scope

P0.1: separate `lumoraf` and `lumorab` repositories, conventions, ignores, examples,
and bootstrap instructions. Existing folder names are retained.

P0.2: Compose PostgreSQL with UTF-8, UTC, persistent storage, healthcheck, and env
configuration. PostgreSQL is the only local infrastructure service needed now.

P0.3: Spring Boot skeleton with database-aware health, OpenAPI, uniform application
and security errors, request IDs, and a deny-by-default security boundary.

P0.4: Next.js shell, shared tokens/components, API client, loading/error states,
and a development connection indicator in the frontend repository.

P0.5: Flyway V1, UUID IDs, `created_at`/`updated_at` audit foundation, and Hibernate
schema validation. No business tables are invented ahead of their owning phases.

## API contract

`GET /api/health` returns HTTP 200:

```json
{"status":"UP","service":"lumorab","database":"UP","timestamp":"2026-10-15T00:00:00Z"}
```

Database failure returns HTTP 503:

```json
{"code":"SERVICE_UNAVAILABLE","message":"The service is temporarily unavailable.","details":{},"requestId":"generated-or-sanitized-id"}
```

Validation errors use HTTP 400 and `VALIDATION_ERROR`, with field messages in
`details`. Malformed payloads use `BAD_REQUEST`; missing public resources use
`NOT_FOUND`. Authentication and permission failures use 401 `UNAUTHORIZED` and
403 `FORBIDDEN`. Unknown protected paths are denied before MVC routing. Unexpected
errors use 500 `INTERNAL_ERROR` without exposing exception messages or SQL.

`X-Request-Id` accepts 1–64 letters/digits/underscores/hyphens; other input is
replaced with a UUID. The identifier is attached to logs and responses.
Transport-level CORS rejections are handled by Spring before MVC error handling.

## Database conventions

- New schema changes always add `V2__description.sql`, `V3__description.sql`, etc.
- Never edit a migration already applied in a shared environment.
- Do not enable `baseline-on-migrate` to hide a nonempty unknown database.
- Hibernate uses `ddl-auto: validate`; Flyway owns DDL.
- Use UUID primary keys, snake_case names and TIMESTAMPTZ audit columns.
- JPA entities extend `AuditableEntity`; Spring Data maintains UTC `Instant` fields.
- For SQL writers, attach a BEFORE UPDATE trigger calling `set_updated_at()`.
- Migration V1 defines the audit function; business tables start in later stages.
- Do not use destructive Flyway clean against shared data (disabled by default).

## Exit gate

Both repositories must pass their checks. With PostgreSQL running, run the backend
integration profile, start the API and frontend, confirm the connection card shows
API and PostgreSQL connected, and open Swagger. Codebase initialization does not
imply these runtime checks passed on every machine.

Stage 1 adds Clerk JWT verification, local users and database-owned roles; room
inventory, authoritative availability/pricing, booking and payment follow later.

## Bootstrap verification

Verified on 15 October 2026 with Java 21 and PostgreSQL 17.11: Maven formatting,
compilation, packaging, eight API/security/error tests, and two integration tests
all pass. The integration checks used a fresh isolated PostgreSQL database,
confirmed the V1 migration applied, then confirmed repeated migration is a no-op.
Health and OpenAPI were exercised over real HTTP.

Docker is not installed on the bootstrap machine. Integration used an ignored,
workspace-local portable PostgreSQL runtime; `docker compose up` still needs to be
verified on a Docker-enabled development machine. The Compose file targets the
PostgreSQL 17 Alpine image and is intended for development, not deployment.
