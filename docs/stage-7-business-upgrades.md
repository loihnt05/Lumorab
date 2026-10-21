# Stage 7 — Business Upgrades (`P7.1–P7.11`)

Stage 7 extends the backend (`Lumorab`) with cancellation policy evaluation, cancellation and idempotent refund processing, multi-provider payment QR (`VIETQR` + `MOCK`), HMAC-verified idempotent webhooks, admin payment reconciliation, check-in QR verification, transactional email outbox notifications, and secure guest room access code delivery.

## 1. Flyway Migration (`V9__stage7_business_upgrades.sql`)

- **`bookings` columns**:
  - `cancelled_at TIMESTAMPTZ`
  - `cancelled_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL`
  - `cancellation_reason VARCHAR(500)`
  - `check_in_qr_token VARCHAR(120) UNIQUE`
- **`payments` columns**:
  - `qr_payload VARCHAR(500)`
  - `refunded_at TIMESTAMPTZ`
- **`refunds` table**:
  - `UNIQUE (booking_id)` enforces one idempotent refund record per cancelled paid booking.
  - Tracks `refund_code`, `refund_amount`, `fee_amount`, `refund_percentage`, `policy_tier`, `status` (`PENDING`, `REFUNDED`, `NO_REFUND`, `FAILED`), and `processed_at`.
- **`payment_webhook_events` table**:
  - `UNIQUE (provider, event_id)` guarantees idempotent webhook processing across retries.
- **`email_notifications` table**:
  - `UNIQUE (idempotency_key)` guarantees at-most-once outbox record per business transition (`BOOKING_CONFIRMED`, `BOOKING_CANCELLED`, `REFUND_UPDATED`).
  - Database check constraint `ck_email_notifications_no_secret` forbids `codeValue` / `code_value` in email bodies.

## 2. Cancellation Policy v1 & Idempotent Refunds (`P7.1`, `P7.2`, `P7.7`)

- **Policy tiers (`CancellationPolicyService`)**:
  - `UNPAID_HOLD`: `PENDING_PAYMENT` bookings can be cancelled with `0` refund and `0` fee.
  - `FULL_REFUND` (`100%`): `CONFIRMED` paid bookings cancelled `>= 48` hours before check-in day (`00:00` in `app.cancellation.zone`).
  - `PARTIAL_REFUND` (`50%`): `CONFIRMED` paid bookings cancelled `24..47` hours before check-in day.
  - `NO_REFUND` (`0%`): `CONFIRMED` paid bookings cancelled `< 24` hours before check-in day.
  - `NOT_CANCELLABLE`: `CHECKED_IN`, `CHECKED_OUT`, `COMPLETED`, `CANCELLED`, and `EXPIRED` bookings cannot be cancelled (`409 CONFLICT`).
- **Endpoints**:
  - `GET /api/me/bookings/{bookingId}/cancellation-policy` & `GET /api/bookings/{bookingId}/cancellation-policy`
  - `POST /api/me/bookings/{bookingId}/cancel`, `POST /api/bookings/{bookingId}/cancel`, `POST /api/manager/bookings/{bookingId}/cancel`, `POST /api/admin/bookings/{bookingId}/cancel`
  - `GET /api/me/bookings/{bookingId}/refund` & `GET /api/bookings/{bookingId}/refund`

## 3. Sandbox/Real Payment Provider (`VIETQR`), Webhooks & Reconciliation (`P7.3–P7.6`, `P7.8`)

- **Payment creation & Payment QR**:
  - `POST /api/bookings/{bookingId}/payments` accepts optional `?provider=VIETQR` or `{"provider": "VIETQR"}` (defaults to `app.payment.default-provider`, `MOCK`).
  - `GET /api/payments/{paymentId}/qr` returns `qrType = "PAYMENT_QR"` with payload `VIETQR|...`. Querying or rendering a Payment QR is read-only and never confirms a payment.
- **Webhook endpoint (`POST /api/webhooks/payments/{provider}`)**:
  - Verifies `X-Webhook-Signature` using constant-time HMAC-SHA256 over `eventId|paymentCode|status|amount|currency|transactionId` with `PAYMENT_WEBHOOK_SECRET`. Forged or missing signatures return `401 UNAUTHORIZED`.
  - Deduplicates retries via `payment_webhook_events (provider, event_id)`.
  - Rejects late webhooks for `EXPIRED` or `CANCELLED` bookings with `409 CONFLICT`.
- **Admin reconciliation**:
  - `POST /api/admin/payments/{paymentId}/reconcile`
  - `POST /api/admin/bookings/{bookingId}/reconcile-payment`

## 4. Check-in QR (`P7.9`) vs Payment QR (`P7.8`)

- Upon booking confirmation (`BookingConfirmationService.confirm`), a unique `check_in_qr_token` (`LMR-CHK-...`) is generated.
- `GET /api/me/bookings/{id}/check-in-qr` (and `/api/bookings/{id}/check-in-qr`) returns `qrType = "CHECK_IN_QR"` for `CONFIRMED` or `CHECKED_IN` bookings.
- `POST /api/manager/check-in-qr/verify` (and `/api/manager/properties/{propertyId}/check-in-qr/verify`) verifies the token for the manager's assigned property:
  - Rejects any Payment QR (`VIETQR|...`) with `400 BAD_REQUEST`.
  - Rejects `CANCELLED`, `EXPIRED`, `CHECKED_OUT`, or `COMPLETED` bookings with `409 CONFLICT`.

## 5. Email Notification Outbox (`P7.10`) & Guest Room Access Code Policy (`P7.11`)

- **Email Outbox**:
  - `GET /api/admin/email-notifications` lists outbox notifications (`BOOKING_CONFIRMED`, `BOOKING_CANCELLED`, `REFUND_UPDATED`).
- **Guest Room Access Code (`GET /api/me/bookings/{bookingId}/access-code`, `GET /api/bookings/{bookingId}/access-code`)**:
  - Restricted strictly to the authenticated booking owner (`404` for other users).
  - Requires booking status in `CONFIRMED` or `CHECKED_IN` and current date in `[checkInDate, checkOutDate)` (`409 CONFLICT` before check-in day, on/after check-out day, or after check-out/cancellation).
  - Audits every guest code read with `ROOM_CODE_READ` (`scope = GUEST`) without logging `codeValue`.
