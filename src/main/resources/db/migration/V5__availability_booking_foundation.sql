-- Read-side booking foundation needed by Stage 3 availability. Stage 4 adds
-- booking codes, price snapshots, guests and transactional booking creation.
CREATE TABLE bookings (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    property_id UUID NOT NULL REFERENCES properties(id),
    check_in_date DATE NOT NULL,
    check_out_date DATE NOT NULL,
    status VARCHAR(24) NOT NULL CHECK (status IN
        ('PENDING_PAYMENT','CONFIRMED','EXPIRED','CANCELLED','CHECKED_IN','CHECKED_OUT','COMPLETED')),
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_booking_dates CHECK (check_in_date < check_out_date),
    CONSTRAINT ck_booking_hold_expiry CHECK (status <> 'PENDING_PAYMENT' OR expires_at IS NOT NULL),
    UNIQUE (id, property_id)
);
CREATE INDEX idx_bookings_user ON bookings(user_id);
CREATE INDEX idx_bookings_property_dates ON bookings(property_id, check_in_date, check_out_date);
CREATE INDEX idx_bookings_status_expiry ON bookings(status, expires_at);
CREATE TRIGGER bookings_updated_at BEFORE UPDATE ON bookings
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE rooms ADD CONSTRAINT uq_rooms_id_property UNIQUE (id, property_id);
CREATE TABLE booking_rooms (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL,
    room_id UUID NOT NULL,
    property_id UUID NOT NULL,
    CONSTRAINT fk_booking_rooms_booking_property FOREIGN KEY (booking_id, property_id)
        REFERENCES bookings(id, property_id),
    CONSTRAINT fk_booking_rooms_room_property FOREIGN KEY (room_id, property_id)
        REFERENCES rooms(id, property_id),
    UNIQUE (booking_id, room_id)
);
CREATE INDEX idx_booking_rooms_room_booking ON booking_rooms(room_id, booking_id);
