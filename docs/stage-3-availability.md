# Stage 3 public catalog and availability

Implements the backend scope of P3.1–P3.4 on top of Stage 2. P3.5 (search UX)
and the frontend work in P3.1/P3.2 remain deferred. No frontend build is required.
Existing Stage 2 `/api/properties` routes retain their contracts.

## Public API

All routes below accept anonymous GET requests. Other methods and unknown public
routes remain denied by the security configuration. Errors retain
`{code,message,details,requestId}` and `X-Request-Id`.

| Path | Response |
| --- | --- |
| `/api/public/properties` | Active property summaries: ID, name, slug, address, city |
| `/api/public/properties/{slug}` | Active property detail, including description, coordinates and check-in/out times; UUID lookup is also supported |
| `/api/public/properties/{propertyId}/rooms` | Visible physical rooms with room type, base price, capacity, galleries and amenities |
| `/api/public/rooms/{roomId}` | One visible physical room |
| `/api/public/availability?propertyId=UUID&checkIn=2027-01-10&checkOut=2027-01-13&guests=2` | Matching rooms with a server-calculated price quote per room |

These routes use dedicated public DTOs without inventory status, blocking flags,
audit timestamps, booking/user information or access codes. Room and type images
are separate ordered galleries (`sortOrder`, then ID); amenities sort by name/ID.
Metadata is loaded in batches rather than once per returned room.
Property images are not part of the Stage 2 schema; images are exposed on rooms
and room types. Catalog listing does not imply date availability.

Property check-in/out times use direct JDBC LocalTime mapping so PostgreSQL TIME
values remain local wall-clock times under a non-UTC JVM. This also fixes the
Stage 2 write/read mapping. Existing data written with the old mapping should be
checked against the intended property times; no speculative data conversion runs.

Hidden or unknown properties/rooms return 404. A valid active property with no
matching rooms returns an empty list. Missing/malformed inputs, nonpositive guest
counts and `checkOut <= checkIn` return 400. Dates use ISO `yyyy-MM-dd` calendar
dates, with checkout exclusive. Stage 3 validates date order but does not impose a
past-date cutoff or booking horizon; booking-create policy belongs in Stage 4.

Example availability response (one result):

```json
{
  "propertyId": "<property UUID>",
  "checkIn": "2027-01-10",
  "checkOut": "2027-01-13",
  "guests": 2,
  "checkedAt": "2026-10-19T10:00:00Z",
  "rooms": [{
    "room": {
      "id": "<room UUID>", "propertyId": "<property UUID>",
      "roomNumber": "No5", "name": "No5 Deluxe", "floor": "2",
      "roomType": {
        "id": "<room type UUID>", "name": "Deluxe", "description": "Garden view",
        "basePrice": 600000.00, "maxGuests": 2, "bedType": "QUEEN", "bedCount": 1,
        "sizeM2": 25.00, "images": [], "amenities": []
      },
      "images": []
    },
    "quote": {
      "numberOfNights": 3, "pricePerNight": 600000.00,
      "subtotal": 1800000.00, "totalAmount": 1800000.00, "currency": "VND"
    }
  }]
}
```

## Inventory and pricing rules

The SQL query requires ACTIVE property, room type and room, `is_blocked=false`,
and `max_guests >= guests`. An overlapping stay occupies its physical rooms when
the booking is CONFIRMED or CHECKED_IN, or PENDING_PAYMENT with `expires_at > now`.
EXPIRED, CANCELLED, CHECKED_OUT and COMPLETED release inventory. The list of
unconditionally occupying statuses is centralized in `BookingStatus`; the pending
expiry predicate exists once in `PublicCatalogRepository.available`.

Overlap is exactly `existing.check_in_date < requested.checkOut AND
existing.check_out_date > requested.checkIn`. Adjacent stays are allowed. The
query uses NOT EXISTS, so multiple booking records do not duplicate results. All
hold comparisons within one search use the same server Clock instant, returned as
`checkedAt`. A hold expiring exactly at that instant no longer occupies inventory,
even if an expiration worker has not changed its status.

`PricingService` calculates calendar nights and multiplies the current database
base price using BigDecimal. The Stage 3 single currency is VND; all inventory
rates must use VND. There are no discounts, taxes, fees or currency conversion in
this version, so subtotal equals total. Quotes are embedded in availability;
there is no separate quote POST endpoint. Client price/total parameters cannot
change the quote. Each new search reads the current rate. Responses use
`Cache-Control: no-store`.

A quote is advisory and does not reserve inventory. Stage 4 must recheck active
inventory, capacity, conflicts and price inside the booking-create transaction,
then persist price snapshots and prevent concurrent double booking. This stage
does not claim to implement that write-side concurrency protection.

## Migration and Stage 4 handoff

V5 adds the minimal `bookings` and `booking_rooms` read model required to test real
booking conflicts now. This intentionally brings part of the Stage 4 schema
forward instead of returning fake availability until Stage 4. V1–V4 are unchanged.

`bookings` holds user/property references, stay dates, status, optional expiry and
audit fields. Pending bookings require an expiry; checkout must follow check-in.
`booking_rooms` links physical rooms to bookings and carries `property_id` for
composite foreign keys that reject cross-property assignments. A booking can
reference multiple distinct rooms. Indexes support room/booking lookup, property
dates, user lookup and status/expiry.

No booking mutation endpoint or production seed booking is added. Stage 4 should
extend these tables through a new migration with booking codes, monetary totals,
room-type/price snapshots and guest data, plus state transitions, create/expiry
services and transaction locking. Existing rows will need an explicit backfill
policy when introducing new non-null fields. Do not recreate or edit V5.

## Verification

```powershell
.\mvnw.cmd verify
# Set DB_* to a dedicated test database; disable background code generation:
$env:ROOM_CODES_ENABLED = 'false'
.\mvnw.cmd -Pintegration verify
```

`PricingServiceTest` covers exact decimal arithmetic, year/leap-day boundaries
and invalid rates/dates. `AvailabilityIT` exercises real PostgreSQL and HTTP:
public DTOs and ordered content; active/blocked/capacity filters; every booking
status; overlapping/adjacent dates; exact hold expiry; multiple rooms; price
changes and untrusted totals; malformed/missing inputs; public authorization;
OpenAPI routes; no access-code exposure; and database integrity constraints.
Existing foundation, identity, scope, inventory and room-code tests also run.

Verified on 19 October 2026 with Java 21 and PostgreSQL 17.11: `clean -Pintegration
verify` passed with 45 unit/API tests and 46 integration tests, including 24 new
availability/catalog cases. V1–V5 applied to an empty isolated database; repeated
migrations passed without changes. Backend formatting, compilation and JAR
packaging passed. Frontend files and builds were not touched.
