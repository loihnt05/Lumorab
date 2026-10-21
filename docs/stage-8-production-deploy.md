# Stage 8 — Production Hardening & Deploy (`P8.1–P8.10`)

Stage 8 finalizes the Lumora Homestay Booking Backend (`Lumorab`) for production deployment with automated regression protection, targeted PostgreSQL query indexes (`V10`), HTTP security headers, per-client rate limiting, liveness/readiness/operational observability, production profile configuration, containerization, CI/CD, database backup/restore drill scripts, and post-deploy operating runbooks.

## 1. Automated Test Baseline (`P8.1`)

- **Unit Tests (`.\mvnw.cmd test`)**:
  - Pricing quotes, date/guest validation, booking/payment state machine transitions, Cancellation Policy v1 matrix (`CancellationPolicyTest`), room access code generation/scheduling/redaction, audit redaction, and rate limiting (`RateLimitFilterTest`).
- **Integration Tests (`.\mvnw.cmd -Pintegration verify`)**:
  - End-to-end verification against PostgreSQL 16 covering Flyway migrations (`V1–V10`), Clerk JWT verification, role/property-scope isolation, inventory CRUD & blocking, availability overlap queries, concurrent booking serialization (`SELECT ... FOR UPDATE`), mock & VietQR payments, HMAC-verified idempotent webhooks, payment reconciliation, cancellation & idempotent refunds, Check-in QR vs Payment QR separation, guest room access code stay-window enforcement, manager dashboard, admin monitor, and Stage 8 security/observability (`Stage8ProductionHardeningIT`).

## 2. Database Indexes & Performance (`P8.2` — `V10__stage8_production_indexes.sql`)

| Index | Target Table & Columns | Query Pattern Accelerated |
| :--- | :--- | :--- |
| `idx_bookings_property_status_checkin` | `bookings(property_id, status, check_in_date, created_at ASC, id ASC)` | Manager dashboard & admin monitor upcoming check-ins (`status = 'CONFIRMED' AND check_in_date >= :date`) |
| `idx_bookings_property_status_checkout` | `bookings(property_id, status, check_out_date, created_at ASC, id ASC)` | Manager dashboard upcoming check-outs (`status = 'CHECKED_IN' AND check_out_date >= :date`) |
| `idx_bookings_active_occupancy` | Partial on `bookings` `WHERE status IN ('PENDING_PAYMENT', 'CONFIRMED', 'CHECKED_IN')` | Availability search & booking conflict checks (`BookingInventoryPolicy.OVERLAPPING_OCCUPANCY`) |
| `idx_bookings_created_global` | `bookings(created_at DESC, id DESC)` | Global admin booking history & monitor date-range queries |
| `idx_rooms_blocked` | Partial on `rooms(property_id, room_number, id) WHERE is_blocked = true` | Blocked rooms list & KPI count in manager/admin dashboards |
| `idx_room_access_codes_lookup` | Partial on `room_access_codes(room_id, valid_date, status) WHERE status = 'ACTIVE'` | Guest, manager, and admin active daily room code lookups |

## 3. Security Hardening (`P8.3`)

- **HTTP Security Headers (`SecurityConfig`)**:
  - `X-Content-Type-Options: nosniff`
  - `X-Frame-Options: DENY`
  - `Referrer-Policy: strict-origin-when-cross-origin`
  - `Permissions-Policy: camera=(), microphone=(), geolocation=()`
- **Rate Limiting (`RateLimitFilter` & `RateLimitProperties`)**:
  - Protects `/api/public/**`, `/api/properties/**`, `/api/amenities`, and `/api/webhooks/payments/**`.
  - Configurable via `RATE_LIMIT_ENABLED` (`true`) and `RATE_LIMIT_REQUESTS_PER_MINUTE` (`600` in dev, `120` in `prod`).
  - Emits `X-RateLimit-Limit` and `X-RateLimit-Remaining`, and returns `429 TOO_MANY_REQUESTS` (`Retry-After: 60`) with a standard `{ code, message, details, requestId }` payload when exceeded.
- **Strict Error Redaction**:
  - `server.error.include-message=never` and `server.error.include-stacktrace=never` prevent internal SQL or stack-trace leakage.

## 4. Observability & Health (`P8.4`)

- `GET /api/health/liveness` — Fast JVM liveness probe (`200 OK`).
- `GET /api/health/readiness` — Verifies PostgreSQL connectivity and checks `flyway_schema_history` for any failed migrations (`200 OK` with `appliedMigrations` count, or `503 SERVICE_UNAVAILABLE`).
- `GET /api/health` — Database-backed health check (`200 OK` / `503 SERVICE_UNAVAILABLE`).
- `GET /api/admin/ops/status` — Admin-only operational snapshot reporting `databaseStatus`, `appliedMigrations`, `overduePendingHolds`, `activeRoomCodesToday`, `pendingPaymentsCount`, `sentEmailsCount`, and `failedEmailsCount`.
- Every HTTP request and log line carries `requestId` (`X-Request-Id`).

## 5. Production Environment Matrix (`P8.5` — `application-prod.yml`)

| Variable | Local / Dev Default | Production (`SPRING_PROFILES_ACTIVE=prod`) |
| :--- | :--- | :--- |
| `PAYMENT_MOCK_ENABLED` | `true` | `false` (disables `/api/mock-payments/**`) |
| `PAYMENT_DEFAULT_PROVIDER` | `MOCK` | `VIETQR` |
| `PAYMENT_WEBHOOK_SECRET` | Dev fallback | **Required** strong HMAC secret |
| `CLERK_ISSUER` | Empty / dev tenant | **Required** HTTPS Clerk production issuer |
| `CLERK_AUTHORIZED_PARTIES` | `http://localhost:3000` | **Required** exact production frontend origin(s) |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | **Required** exact production frontend origin(s) |
| `SWAGGER_ENABLED` | `true` | `false` |
| `RATE_LIMIT_REQUESTS_PER_MINUTE` | `600` | `120` |

## 6. Low-Cost Hosting Architecture & CI/CD (`P8.6`, `P8.7`)

- **Container Runtime**: Multi-stage [Dockerfile](file:///d:/workspace/lumora/Lumorab/Dockerfile) (`eclipse-temurin:21-jdk-alpine` build $\rightarrow$ `eclipse-temurin:21-jre-alpine` non-root `lumora` user, `-XX:MaxRAMPercentage=75.0`, built-in `HEALTHCHECK`).
- **Recommended Low-Cost Topology**:
  - **Frontend (`Lumoraf`)**: Vercel / Cloudflare Pages (Free tier).
  - **Backend (`Lumorab`)**: Render / Railway / Fly.io container or 1-vCPU/1–2 GB RAM VPS running the Docker image with `SPRING_PROFILES_ACTIVE=prod`.
  - **Database**: Managed PostgreSQL 16 (Neon / Supabase / Render PostgreSQL or private Docker volume on VPS with daily automated `pg_dump` to object storage).
- **CI/CD Pipeline**: [.github/workflows/ci-cd.yml](file:///d:/workspace/lumora/Lumorab/.github/workflows/ci-cd.yml) runs Spotless formatting checks, unit tests, and full PostgreSQL integration tests on every push and pull request.

## 7. Database Backup, Restore Drill & Post-Deploy Checklist (`P8.8–P8.10`)

- **Backup**:
  ```powershell
  .\scripts\db-backup.ps1
  ```
- **Restore Drill**:
  ```powershell
  .\scripts\db-restore.ps1 -BackupFile .\backups\homestay_booking-<timestamp>.dump
  ```
- **Post-Deploy Smoke Test**:
  ```powershell
  .\scripts\smoke-test.ps1 -BaseUrl https://api.your-domain.com
  ```
- **Daily / Weekly Operating Checklist (`P8.10`)**:
  1. Verify `GET /api/health/readiness` returns `READY` and `GET /api/admin/ops/status` shows `overduePendingHolds = 0` and `failedEmailsCount = 0`.
  2. Confirm `activeRoomCodesToday` matches active rooms for the current Vietnam date (`Asia/Ho_Chi_Minh`).
  3. Reconcile any stale `PENDING` payments using `POST /api/admin/payments/{paymentId}/reconcile`.
  4. Verify automated PostgreSQL backups and run `scripts/db-restore.ps1` monthly against a disposable database.
