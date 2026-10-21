-- Stage 8 (P8.2): Production query indexes derived from availability, booking,
-- manager dashboard, admin monitor, and room access code query patterns.

CREATE INDEX idx_bookings_property_status_checkin
    ON bookings(property_id, status, check_in_date, created_at ASC, id ASC);

CREATE INDEX idx_bookings_property_status_checkout
    ON bookings(property_id, status, check_out_date, created_at ASC, id ASC);

CREATE INDEX idx_bookings_active_occupancy
    ON bookings(id, property_id, check_in_date, check_out_date, status, expires_at)
    WHERE status IN ('PENDING_PAYMENT', 'CONFIRMED', 'CHECKED_IN');

CREATE INDEX idx_bookings_created_global
    ON bookings(created_at DESC, id DESC);

CREATE INDEX idx_rooms_blocked
    ON rooms(property_id, room_number, id)
    WHERE is_blocked = true;

CREATE INDEX idx_room_access_codes_lookup
    ON room_access_codes(room_id, valid_date, status)
    WHERE status = 'ACTIVE';
