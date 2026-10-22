# Stage 5 mock payment

Implements backend P5.1–P5.3 and P5.5: the `payments` table, `PaymentGateway`
abstraction, `MockPaymentGateway`, booking payment attempt creation/read, and
mock payment callback endpoints (`success`, `fail`, `cancel`) with transactional
booking transition and idempotency guarantees.

## API and permissions

All endpoints require a verified Clerk session and an active local user.
Mock payment action endpoints also require `app.payment.mock-enabled=true`
(`PAYMENT_MOCK_ENABLED`, default `true` in local development).

| Method | Path | Permission / result |
| --- | --- | --- |
| POST | `/api/bookings/{bookingId}/payments` | Booking owner; creates `PENDING` mock payment or returns existing active `PENDING` payment |
| GET | `/api/bookings/{bookingId}/payments` | Booking owner, assigned MANAGER, or ADMIN; lists payment attempts newest first |
| POST | `/api/mock-payments/{paymentId}/success` | Booking owner or ADMIN; marks payment `PAID` and confirms booking (`CONFIRMED`) |
| POST | `/api/mock-payments/{paymentId}/fail` | Booking owner or ADMIN; marks payment `FAILED`, keeps booking `PENDING_PAYMENT` while hold is unexpired |
| POST | `/api/mock-payments/{paymentId}/cancel` | Booking owner or ADMIN; marks payment `CANCELLED` and cancels booking (`CANCELLED`) |

## Transactions, lock order and idempotency

All payment mutations run in `READ COMMITTED` transactions and preserve the
global lock order:

1. Lock physical `rooms` in UUID order (`FOR UPDATE`) via `bookings.roomIds(bookingId)`.
2. Lock the `bookings` row (`FOR UPDATE`).
3. Lock the `payments` row (`FOR UPDATE`).

Key invariants:
- `amount` and `currency` are read exclusively from the locked `bookings` snapshot;
  client request payloads never control payment amount.
- At most one `PAID` payment can exist per booking (`uq_payments_booking_paid` partial unique index).
- Duplicate `POST /api/mock-payments/{paymentId}/success` on an already `PAID`
  payment is an idempotent no-op returning the current `PaymentActionResponse`.
- Late `success` after `booking.expires_at <= now` transitions the booking to
  `EXPIRED`, marks the payment `FAILED`, and returns HTTP `409 CONFLICT` (`BOOKING_CONFLICT`).
- When `fail` is called while the booking hold is still valid (`expires_at > now`),
  the booking stays `PENDING_PAYMENT` and the guest can create a new payment
  attempt before `expires_at`.
- When `cancel` is called on a `PENDING` payment, the payment becomes `CANCELLED`
  and the booking transitions from `PENDING_PAYMENT` to `CANCELLED`, immediately
  releasing the room inventory.

## Configuration and migration

| Environment variable | Default | Purpose |
| --- | --- | --- |
| `PAYMENT_MOCK_ENABLED` | `true` | Enables `/api/mock-payments/**` action endpoints |

`V7__payments.sql` creates `payments` with status check constraints
(`PENDING`, `PAID`, `FAILED`, `CANCELLED`, `REFUNDED`), timestamp consistency
checks, partial unique index `uq_payments_booking_paid`, lookup index
`idx_payments_booking_created`, and the `payments_updated_at` trigger.
