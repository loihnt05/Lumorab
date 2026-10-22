# Stage 6 operations and monitoring

Implements backend P6.1–P6.5, P6.7 and P6.8: manager property dashboard,
check-in/check-out/complete booking lifecycle, admin user role/status management
with last-admin protection, admin manager-property assignment aliases, admin
system monitor (`summary`, `revenue`, `bookings`), manager/admin current room
access code read with audit logging, and redacted audit log query APIs.

## API and permissions

All endpoints require a verified Clerk session and an active local user.
Manager endpoints enforce property-level assignment (`user_property_assignments`)
for `MANAGER` callers while allowing `ADMIN` global access.

### Manager operations & access codes

| Method | Path | Permission / result |
| --- | --- | --- |
| GET | `/api/manager/properties/{propertyId}/dashboard` | Assigned MANAGER or ADMIN; today's arrivals, departures, in-house stays, room status summary (`totalRooms`, `availableRooms`, `occupiedRooms`, `blockedRooms`), and recent bookings |
| POST | `/api/manager/bookings/{id}/check-in` | Assigned MANAGER or ADMIN; transitions `CONFIRMED -> CHECKED_IN` when `today >= checkInDate` and `today < checkOutDate` |
| POST | `/api/manager/bookings/{id}/check-out` | Assigned MANAGER or ADMIN; transitions `CHECKED_IN -> CHECKED_OUT` |
| POST | `/api/manager/bookings/{id}/complete` | Assigned MANAGER or ADMIN; transitions `CHECKED_OUT -> COMPLETED` |
| POST | `/api/manager/properties/{propertyId}/bookings/{id}/check-in` | Property-scoped alias for check-in |
| POST | `/api/manager/properties/{propertyId}/bookings/{id}/check-out` | Property-scoped alias for check-out |
| POST | `/api/manager/properties/{propertyId}/bookings/{id}/complete` | Property-scoped alias for complete |
| GET | `/api/manager/properties/{propertyId}/rooms/{roomId}/access-code` | Assigned MANAGER or ADMIN; returns today's active 6-digit code and logs `ROOM_CODE_READ` |
| GET | `/api/manager/rooms/{roomId}/access-code` | Assigned MANAGER or ADMIN; resolves room's property and returns today's active 6-digit code |
| GET | `/api/admin/rooms/{roomId}/access-code` | ADMIN; returns today's active 6-digit code and logs `ROOM_CODE_READ` |
| POST | `/api/admin/room-codes/generate-today` | ADMIN; triggers idempotent daily code generation and logs `ROOM_CODE_GENERATE_TODAY` |

### Admin user & assignment management

| Method | Path | Permission / result |
| --- | --- | --- |
| GET | `/api/admin/users` | ADMIN; paginated user list (`page`, `size`, optional `role`, `status`, `q` filters) |
| PATCH | `/api/admin/users/{userId}/role` | ADMIN; updates role (`USER`, `MANAGER`, `ADMIN`) with Last-Admin guard and `USER_ROLE_CHANGE` audit log |
| PATCH | `/api/admin/users/{userId}/status` | ADMIN; updates status (`ACTIVE`, `DISABLED`) with Last-Admin guard and `USER_STATUS_CHANGE` audit log |
| GET | `/api/admin/users/{userId}/properties` | ADMIN; lists assigned properties (also mapped at `/api/admin/managers/{userId}/properties`) |
| POST | `/api/admin/users/{userId}/properties` | ADMIN; assigns property to `MANAGER` and logs `MANAGER_PROPERTY_ASSIGN` (also mapped at `/api/admin/managers/{userId}/properties`) |
| PUT | `/api/admin/users/{userId}/properties` | ADMIN; replaces assigned properties for `MANAGER` and logs `MANAGER_PROPERTY_ASSIGN` (also mapped at `/api/admin/managers/{userId}/properties`) |
| DELETE | `/api/admin/users/{userId}/properties/{propertyId}` | ADMIN; removes property assignment and logs `MANAGER_PROPERTY_UNASSIGN` (also mapped at `/api/admin/managers/{userId}/properties/{propertyId}`) |

### Admin monitor & audit logs

| Method | Path | Permission / result |
| --- | --- | --- |
| GET | `/api/admin/monitor/summary` | ADMIN; global KPIs (`from`, `to`, optional `propertyId`) including properties, rooms, bookings by status, occupancy rate, and simulated paid revenue |
| GET | `/api/admin/monitor/revenue` | ADMIN; simulated revenue from `PAID` mock payments (`from`, `to`, optional `propertyId`), daily breakdown and per-property breakdown |
| GET | `/api/admin/monitor/bookings` | ADMIN; booking trend breakdown by date and status (`from`, `to`, optional `propertyId`) |
| GET | `/api/admin/audit-logs` | ADMIN; paginated audit log search (`page`, `size`, optional `action`, `targetType`, `actorUserId`) |

## Security and audit redaction invariants

- **Last-Admin Guard**: `UserQueryService` locks active `ADMIN` rows (`FOR UPDATE`)
  before demoting an `ADMIN` or disabling an `ADMIN`. If the target is the sole
  remaining active `ADMIN`, the request fails with `409 CONFLICT`.
- **Guest Access-Code Isolation**: Guest booking endpoints (`/api/bookings/**`,
  `/api/me/bookings/**`) never expose `room_access_codes`.
- **Audit Log Secret Redaction**: `AuditService` automatically redacts sensitive
  keys (`codeValue`, `accessCode`, `pin`, `secret`, `password`, `token`) and
  standalone 6-digit PIN sequences (`[REDACTED]`), reinforced by a PostgreSQL
  `CHECK` constraint (`chk_audit_logs_no_code_value`) in `V8__operations_and_audit_logs.sql`.
