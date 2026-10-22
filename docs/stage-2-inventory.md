# Stage 2 backend inventory

Implements the backend work in P2.1, P2.2 and P2.4–P2.9. P2.3 and every frontend
task remain deferred. Stage 1 identity and database-owned authorization remain in
force. No frontend session, screen or Clerk metadata grants inventory permissions.

## Schema and lifecycle

Flyway V4 extends the existing `properties` table and adds `room_types`, `rooms`,
`amenities`, `room_type_amenities`, `room_images` and `room_access_codes`. V1–V3 are
unchanged. Hibernate continues to validate the schema rather than create it.

- Property slugs are unique lowercase ASCII words separated by hyphens.
- Room types belong to a property. Price and capacity must be positive; price uses
  decimal amounts with two fractional digits. No currency conversion is performed.
- Each room has a physical room number unique within its property. Surrounding
  whitespace is trimmed; comparison is case-sensitive. A composite foreign key
  prevents a room referencing a room type from another property, even through SQL.
- `ACTIVE`/`INACTIVE` is independent of `isBlocked`. Only ADMIN can change blocking.
  Concurrent manager edits and admin blocks lock the room row to avoid lost changes.
- DELETE on properties, room types and rooms deactivates the record. It never
  destroys room references or code history. PUT can reactivate it.
- New rooms and active room edits require an active room type. Public queries require
  active property, room type and room, and hide blocked rooms. These are catalog
  queries; date-based availability, booking conflict checks and pricing come later.

## Permissions

| Operation | Anonymous / USER | Assigned MANAGER | ADMIN |
| --- | --- | --- | --- |
| Read active public catalog and amenities | Yes | Yes | Yes |
| Create, update or deactivate property | No | No | Yes |
| Manage room types, rooms, images and amenity selection | No | Assigned property only | All properties |
| Create, update or delete amenity dictionary entries | No | No | Yes |
| Block/unblock rooms | No | No | Yes |
| Read room access codes or trigger daily generation | No | No | Yes |

Managers retain Stage 1 property summary reads. Property editing and access-code
viewing are admin-only until an explicit manager business policy is introduced.
All mutation services enforce authorization independently of controller paths.
Child resource IDs are checked against the property/owner in the request URL.

## API

Bodies use camelCase. Responses are DTOs, never JPA entities. Errors retain
`{code,message,details,requestId}` and `X-Request-Id`. Invalid bodies return 400,
forbidden scope 403, missing/hidden resources 404, duplicate or referenced data 409.

| Method | Path | Result |
| --- | --- | --- |
| GET | `/api/properties` | Active property details |
| GET | `/api/properties/{idOrSlug}` | One active property |
| GET | `/api/admin/properties[/{id}]` | All statuses, list or detail |
| POST | `/api/admin/properties` | Create, 201 |
| PUT / DELETE | `/api/admin/properties/{id}` | Full update / deactivate |
| GET | `/api/properties/{propertyId}/room-types[/{id}]` | Active types, including images and amenities |
| GET | `/api/properties/{propertyId}/rooms[/{id}]` | Public rooms, including type details and room images |
| GET / POST | `/api/manager/properties/{propertyId}/room-types` | All statuses / create |
| GET / PUT / DELETE | `/api/manager/properties/{propertyId}/room-types/{id}` | Read / full update / deactivate |
| GET / POST | `/api/manager/properties/{propertyId}/rooms` | All statuses / create |
| GET / PUT / DELETE | `/api/manager/properties/{propertyId}/rooms/{id}` | Read / full update / deactivate |
| PATCH | `/api/admin/rooms/{id}` | `{ "isBlocked": true }` or false |
| GET | `/api/amenities` | Amenity dictionary |
| POST | `/api/admin/amenities` | Create `{ "name": "Wi-Fi", "icon": "wifi" }` |
| PUT / DELETE | `/api/admin/amenities/{id}` | Update / delete unused amenity |
| PUT | `/api/manager/properties/{propertyId}/room-types/{typeId}/amenities` | Replace selection: `{ "amenityIds": ["uuid"] }` |
| POST | `/api/manager/properties/{propertyId}/{roomsOrRoomTypes}/{ownerId}/images` | Create image metadata, 201 |
| PUT / DELETE | Same image path plus `/{imageId}` | Update / delete image metadata |
| GET | `/api/admin/rooms/{roomId}/access-code?date=2026-10-16` | Code for date; defaults to today in configured zone |
| POST | `/api/admin/room-access-codes/generate` | Generate today's missing codes; returns date/eligible/generated counts |

`[/{id}]` denotes separate list and detail routes. `roomsOrRoomTypes` is literally
`rooms` or `room-types`. Creates return 201, updates/reads 200, deletes 204.
Swagger/OpenAPI expose the concrete routes and request schemas.

Property create/update example:

```json
{
  "name": "Lumora Da Lat", "slug": "lumora-da-lat",
  "description": "A quiet homestay", "address": "12 Example Street", "city": "Da Lat",
  "latitude": 11.940400, "longitude": 108.458300,
  "checkInTime": "14:00", "checkOutTime": "12:00", "status": "ACTIVE"
}
```

Room type create/update example:

```json
{
  "name": "Deluxe", "description": "Garden view", "basePrice": 750000.00,
  "maxGuests": 2, "bedType": "QUEEN", "bedCount": 1, "sizeM2": 25.00, "status": "ACTIVE"
}
```

Room create/update example (`roomTypeId` must be a real UUID):

```json
{
  "roomTypeId": "00000000-0000-0000-0000-000000000000",
  "roomNumber": "No5", "name": "No5 Deluxe Room", "floor": "2", "status": "ACTIVE"
}
```

Generic room writes do not accept a mutable blocking field. Full updates replace
optional fields too; omit them only when clearing them is intended.

## Images and amenities

Image bodies contain `imageUrl`, nonnegative `sortOrder`, and boolean `isCover`.
HTTP(S) URLs and local absolute paths such as `/images/deluxe.jpg` are accepted.
Only metadata is stored; there is no upload, URL fetching or object storage yet.
Each image belongs to exactly one room or one room type. Selecting a cover clears
the previous cover under an owner-row lock, with a unique database index as a second
guard. Galleries sort by `sortOrder`, then ID. Room details contain their own
gallery plus the type's gallery and amenities. Cover images are optional.

Amenity selection is replaced atomically. Unknown amenity IDs reject the entire
change. Dictionary deletion returns 409 while any room type references the entry.

## Daily access codes

Defaults are midnight in `Asia/Ho_Chi_Minh`, six decimal characters generated using
`SecureRandom`, with leading zeros preserved. The schema stores a string up to 128
characters rather than enforcing six digits. Generation length can be 4–12.

| Environment variable | Default | Meaning |
| --- | --- | --- |
| `ROOM_CODES_ENABLED` | `true` | Enable scheduled and startup generation |
| `ROOM_CODES_ZONE` | `Asia/Ho_Chi_Minh` | Calendar-day and cron timezone |
| `ROOM_CODES_CRON` | `0 0 0 * * *` | Spring six-field daily cron |
| `ROOM_CODES_LENGTH` | `6` | Number of generated decimal characters |

On startup the job catches up for today; it also runs on the daily schedule.
ADMIN can safely run it again after creating/activating rooms or recovering from a
failure. Disabled scheduling does not disable the admin endpoint. Historical gaps
are not automatically backfilled and the HTTP endpoint cannot rotate arbitrary dates.

The job includes rooms whose room, type and property are active. Blocked active
rooms still receive codes because blocking affects booking availability, not lock
maintenance. Inactive inventory is skipped. Existing history is retained.

A transaction-scoped PostgreSQL advisory lock serializes workers across instances.
`UNIQUE(room_id,valid_date)` plus `ON CONFLICT DO NOTHING` preserves an existing
code on retries, including revoked records. A failure rolls back that day's work;
the endpoint can be retried. New dates get a code different from the most recent
previous code. No code values appear in batch summaries or DTO `toString()` output.
The read endpoint returns only ACTIVE codes and sends `Cache-Control: no-store`.

This foundation stores recoverable codes in the database. Application-level
encryption/key management and guest/smart-lock delivery are not implemented in
Stage 2. Code data must be excluded from SQL bind logging and request/response
body tracing when those are enabled externally.

## Verification

```powershell
.\mvnw.cmd verify
# Configure a dedicated disposable PostgreSQL database first:
.\mvnw.cmd -Pintegration verify
```

The integration profile covers clean/repeated migrations, real signed JWTs and
HTTP authorization, catalog validation/visibility, foreign keys, duplicates,
deactivation, admin-only blocking, concurrent room edits/covers, scoped metadata,
atomic amenity selections, and code history/rotation/concurrent retries. Test data
is unique per run and cleanup targets only fixtures. Set `ROOM_CODES_ENABLED=false`
for isolated tests; the inventory integration class does this explicitly.

Verified on 16 October 2026 with Java 21 and PostgreSQL 17.11: formatting,
compilation and packaging passed, with 42 unit/API tests and 21 integration tests
passing. V1–V4 applied successfully to an empty dedicated database and subsequent
migration runs were no-ops. The scheduler's enabled/disabled behavior and Swagger
bearer declarations were also verified. No frontend files were changed by this work.
