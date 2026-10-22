-- V5 only stored read-side fixtures. Preserve them with deterministic legacy codes
-- and initial snapshots from current inventory; historical prices did not exist.
ALTER TABLE bookings
    ADD COLUMN booking_code VARCHAR(64),
    ADD COLUMN guest_count INTEGER NOT NULL DEFAULT 1 CHECK (guest_count > 0),
    ADD COLUMN subtotal NUMERIC(18,2),
    ADD COLUMN discount_amount NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (discount_amount >= 0),
    ADD COLUMN total_amount NUMERIC(18,2),
    ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'VND' CHECK (currency ~ '^[A-Z]{3}$'),
    ADD COLUMN special_request VARCHAR(2000);

ALTER TABLE booking_rooms
    ADD COLUMN room_type_id UUID REFERENCES room_types(id),
    ADD COLUMN room_number VARCHAR(50),
    ADD COLUMN room_name VARCHAR(255),
    ADD COLUMN price_per_night NUMERIC(14,2),
    ADD COLUMN number_of_nights INTEGER,
    ADD COLUMN room_total NUMERIC(18,2);

UPDATE booking_rooms br SET room_type_id=r.room_type_id, room_number=r.room_number,
    room_name=r.name, price_per_night=t.base_price,
    number_of_nights=b.check_out_date-b.check_in_date,
    room_total=t.base_price*(b.check_out_date-b.check_in_date)
FROM rooms r, room_types t, bookings b
WHERE r.id=br.room_id AND t.id=r.room_type_id AND b.id=br.booking_id;

UPDATE bookings b SET booking_code='LEGACY-' || b.id::text,
    subtotal=COALESCE((SELECT sum(br.room_total) FROM booking_rooms br WHERE br.booking_id=b.id),0),
    total_amount=COALESCE((SELECT sum(br.room_total) FROM booking_rooms br WHERE br.booking_id=b.id),0);

ALTER TABLE bookings
    ALTER COLUMN booking_code SET NOT NULL,
    ALTER COLUMN subtotal SET NOT NULL,
    ALTER COLUMN total_amount SET NOT NULL,
    ALTER COLUMN guest_count DROP DEFAULT,
    ADD CONSTRAINT uq_booking_code UNIQUE (booking_code),
    ADD CONSTRAINT ck_booking_amounts CHECK (subtotal >= 0 AND discount_amount <= subtotal
        AND total_amount = subtotal-discount_amount);
ALTER TABLE booking_rooms
    ALTER COLUMN room_type_id SET NOT NULL,
    ALTER COLUMN room_number SET NOT NULL,
    ALTER COLUMN room_name SET NOT NULL,
    ALTER COLUMN price_per_night SET NOT NULL,
    ALTER COLUMN number_of_nights SET NOT NULL,
    ALTER COLUMN room_total SET NOT NULL,
    ADD CONSTRAINT ck_booking_room_price CHECK (price_per_night > 0 AND number_of_nights > 0
        AND room_total = price_per_night*number_of_nights);

CREATE TABLE booking_guests (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL REFERENCES bookings(id),
    full_name VARCHAR(255) NOT NULL CHECK (length(trim(full_name)) > 0),
    phone VARCHAR(32),
    email VARCHAR(320),
    is_primary_guest BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_booking_guests_booking ON booking_guests(booking_id);
CREATE UNIQUE INDEX uq_booking_primary_guest ON booking_guests(booking_id) WHERE is_primary_guest;
CREATE INDEX idx_bookings_user_created ON bookings(user_id, created_at DESC, id DESC);
CREATE INDEX idx_bookings_property_created ON bookings(property_id, created_at DESC, id DESC);
