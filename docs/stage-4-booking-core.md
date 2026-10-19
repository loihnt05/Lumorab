# Stage 4 booking core

Implements backend P4.1–P4.7: transactional single-room booking creation, price
snapshots, inventory holds, expiry, owner history and scoped operations reads.
Frontend screens/builds remain deferred. Payment endpoints are Stage 5 work.

## API and permissions

All endpoints require a verified Clerk session and an active local user. Business
roles and assignments come from PostgreSQL. Responses use `Cache-Control: no-store`;
errors retain `{code,message,details,requestId}` and `X-Request-Id`.

| Method | Path | Permission / result |
| --- | --- | --- |
| POST | `/api/bookings` | USER role; creates own PENDING_PAYMENT booking, 201 and Location header |
| GET | `/api/me/bookings` | Current user's paginated bookings |
| GET | `/api/me/bookings/{id}` | Current user's detail; another owner's ID returns 404 |
| GET | `/api/manager/properties/{propertyId}/bookings` | Assigned MANAGER or ADMIN; paginated property bookings |
| GET | `/api/manager/properties/{propertyId}/bookings/{id}` | Same scope; wrong-property booking returns 404 |
| GET | `/api/admin/bookings` | ADMIN global list; optional `propertyId` filter |
| GET | `/api/admin/bookings/{id}` | ADMIN detail |

Create currently requires role USER, consistent with P4.2. Manager/admin accounts
can read operations data but do not use the guest-create endpoint. Self-history
remains accessible if an existing guest later changes role.

Create example:

```json
{
  "propertyId": "<property UUID>",
  "roomId": "<physical room UUID>",
  "checkIn": "2027-01-10",
  "checkOut": "2027-01-13",
  "guests": 2,
  "primaryGuest": {
    "fullName": "Nguyen Van A",
    "phone": "0900000000",
    "email": "guest@example.com"
  },
  "specialRequest": "Quiet room if possible"
}
```

Names, phone and valid email are required for the primary guest. `guests` is the
total occupancy count; only the primary contact is collected in this MVP. Schema
supports additional guest records and multiple booking-room lines for later work.
Dates are checkout-exclusive, must be ordered, and use four-digit years 1–9999.
Check-in cannot precede today in `BOOKING_ZONE`. Default maximum stay is 365 nights.
Room/property mismatch or missing room returns 404; blocked/inactive inventory,
insufficient capacity or an overlapping live hold/booking returns 409. Invalid
input returns 400. Client user ID, status, expiry and amount fields are never used
as business inputs.

Detail responses contain:

- `booking`: ID, unique `bookingCode`, property ID/name, dates, persisted status,
  guest count, total, currency, expiry and creation time.
- `subtotal`, `discountAmount`, `specialRequest`.
- `rooms`: immutable room ID/type ID, room number/name, nightly price, nights and
  line total captured at creation.
- `guests`: primary guest contact details; no access codes or Clerk identity data.

Lists return `{items,page,size,totalElements}`; items are booking summaries without
guest contact details. Defaults: `page=0`, `size=20`; maximum size is 100. Sort is
`created_at DESC,id DESC`. Optional filters are `date`, `status`, `bookingCode`
(exact match) and `roomId`. Date means the stay contains that date:
`check_in_date <= date AND check_out_date > date`. Scope checks apply before all
filters; supplying another property/room never expands access.

## Transactions and concurrency

Create uses READ COMMITTED and locks the physical room with `FOR UPDATE` before
checking availability. It then acquires shared locks on the current room type and
property so rate/capacity/status edits cannot change the values mid-creation.
After acquiring locks it reads one clock instant, checks active/unblocked inventory,
capacity and date policy, and applies the same overlap/occupancy predicate as public
search (`BookingInventoryPolicy`). It recalculates the quote from the locked
database rate and inserts booking, room snapshot and primary guest in one transaction.
An error in any insert rolls back everything.

Requests for the same room serialize, including adjacent stays, then recheck the
committed occupancy. Different rooms can share read locks on their type/property.
Adjacent dates succeed; overlapping dates yield one hold and one 409. Admin room
blocking and room edits already lock the same room row. Snapshot prices and room
labels do not change when inventory is edited later. Booking codes use `LM-` plus
the full UUID (without hyphens) and have a database unique constraint.

`BookingConfirmationService.confirm` is an internal service boundary for Stage 5,
with no public/admin/mock HTTP confirmation route. A future payment service must
call it only after server-side payment verification, within the same READ COMMITTED
transaction as recording payment success. Confirmation locks all associated rooms
in UUID order, then the booking; verifies PENDING_PAYMENT, unexpired hold and no
other occupying booking; then changes state. Duplicate confirmation is a no-op.
Late confirmation fails even if the expiry worker has not run. A subsequent admin
block does not cancel an already valid hold. The worker's conditional update cannot
overwrite CONFIRMED. Never lock a booking first and then rooms in future payment or
cancellation code: preserve the room-before-booking lock order.

The concurrency guarantee covers application create/confirmation paths. This
stage does not add an exclusion constraint that rejects arbitrary direct SQL
overlaps; future booking mutations must reuse this transaction/locking protocol.
Search/quote never reserves inventory. There is no create-request idempotency key
yet; an immediate duplicate create conflicts while its original hold remains live.

## States and expiry

Allowed transitions are centralized in `BookingStatus`:

| From | To |
| --- | --- |
| PENDING_PAYMENT | CONFIRMED, EXPIRED, CANCELLED |
| CONFIRMED | CHECKED_IN, CANCELLED |
| CHECKED_IN | CHECKED_OUT |
| CHECKED_OUT | COMPLETED |
| EXPIRED / CANCELLED / COMPLETED | None |

Cancellation and check-in/out HTTP workflows come in later stages. The available
transition graph does not itself authorize those operations.

Pending holds occupy inventory only while `expires_at > now`; at the exact expiry
instant the room is released, independently of scheduler timing. The worker selects
the oldest due IDs up to the batch limit and conditionally updates each in its own
transaction. One failed row is counted/logged and does not roll back other rows.
Concurrent workers/retries are safe: updates require both PENDING_PAYMENT and
`expires_at <= capturedNow`. Confirmed records are never expired. Logs contain
counts/IDs/error categories, not guest contact information or access codes.
Read APIs report persisted status, so a just-expired hold can temporarily display
PENDING_PAYMENT with a past `expiresAt` until the next worker run.

| Environment variable | Default | Purpose |
| --- | --- | --- |
| `BOOKING_HOLD_DURATION` | `15m` | Hold duration, 1 second–1 day |
| `BOOKING_ZONE` | `Asia/Ho_Chi_Minh` | Calendar day for create-date validation |
| `BOOKING_MAX_STAY_NIGHTS` | `365` | Maximum stay length, configuration range 1–3650 |
| `BOOKING_EXPIRY_ENABLED` | `true` | Enable scheduled expiry; internal worker remains callable when false |
| `BOOKING_EXPIRY_INTERVAL` | `30s` | Fixed delay between bounded worker batches |
| `BOOKING_EXPIRY_BATCH_SIZE` | `100` | Maximum candidate bookings per batch, 1–1000 |

## Migration

V6 extends V5, leaving V1–V5 unchanged. It adds booking code, guest count, amounts,
currency and special request; room type/price/night/name snapshots; and
`booking_guests` with at most one primary guest per booking. Existing composite
foreign keys still enforce room/property scope. Checks enforce positive line
prices/nights, line multiplication and header total/subtotal/discount consistency.

V5 had no original monetary/contact data. For existing V5 read-side records, V6
uses `LEGACY-<UUID>` codes, the current room/type price and name as the initial
snapshot, VND currency, zero discount and guest count 1. It creates no invented
guest contacts. A legacy booking without room lines receives zero totals. This
backfill is an explicit compatibility policy, not a reconstruction of historical
payments; legacy records need review before commercial use. New API bookings always
include one room, positive calculated amounts and one primary guest.

## Verification

```powershell
.\mvnw.cmd clean verify
# Configure DB_* for a disposable PostgreSQL database:
$env:ROOM_CODES_ENABLED = 'false'
$env:BOOKING_EXPIRY_ENABLED = 'false'
.\mvnw.cmd clean -Pintegration verify
```

Unit tests cover every state transition, scheduler configuration/retry and per-row
failure isolation. PostgreSQL/HTTP integration tests cover owner/role/property
authorization, date/capacity/visibility checks, forged amounts and ownership,
immutable price snapshots, atomic rollback, pagination/filters, configurable hold
expiry, concurrent workers, create/create and create/confirmation contention,
confirmation/expiry races and concurrent admin blocking. Lock tests wait for actual
PostgreSQL lock contention before releasing the room. Migration tests upgrade
populated V5 data to V6 and validate idempotent reruns in an isolated test schema.
