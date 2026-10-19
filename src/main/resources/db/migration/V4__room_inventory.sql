ALTER TABLE properties
    ADD COLUMN description TEXT,
    ADD COLUMN address VARCHAR(500),
    ADD COLUMN city VARCHAR(120),
    ADD COLUMN latitude NUMERIC(9,6) CHECK (latitude BETWEEN -90 AND 90),
    ADD COLUMN longitude NUMERIC(9,6) CHECK (longitude BETWEEN -180 AND 180),
    ADD COLUMN check_in_time TIME NOT NULL DEFAULT '14:00',
    ADD COLUMN check_out_time TIME NOT NULL DEFAULT '12:00';
CREATE INDEX idx_properties_status ON properties(status);

CREATE TABLE room_types (
    id UUID PRIMARY KEY,
    property_id UUID NOT NULL REFERENCES properties(id),
    name VARCHAR(255) NOT NULL CHECK (length(trim(name)) > 0),
    description TEXT,
    base_price NUMERIC(14,2) NOT NULL CHECK (base_price > 0),
    max_guests INTEGER NOT NULL CHECK (max_guests > 0),
    bed_type VARCHAR(100),
    bed_count INTEGER NOT NULL CHECK (bed_count > 0),
    size_m2 NUMERIC(8,2) CHECK (size_m2 > 0),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(id, property_id)
);
CREATE INDEX idx_room_types_property_status ON room_types(property_id, status);
CREATE TRIGGER room_types_updated_at BEFORE UPDATE ON room_types
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE rooms (
    id UUID PRIMARY KEY,
    property_id UUID NOT NULL REFERENCES properties(id),
    room_type_id UUID NOT NULL,
    room_number VARCHAR(50) NOT NULL CHECK (length(trim(room_number)) > 0),
    name VARCHAR(255) NOT NULL CHECK (length(trim(name)) > 0),
    floor VARCHAR(50),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
    is_blocked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_rooms_type_property FOREIGN KEY (room_type_id, property_id)
        REFERENCES room_types(id, property_id),
    CONSTRAINT uq_rooms_number UNIQUE(property_id, room_number)
);
CREATE INDEX idx_rooms_property_status_blocked ON rooms(property_id, status, is_blocked);
CREATE INDEX idx_rooms_type ON rooms(room_type_id);
CREATE TRIGGER rooms_updated_at BEFORE UPDATE ON rooms
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE amenities (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL UNIQUE CHECK (length(trim(name)) > 0),
    icon VARCHAR(100)
);
CREATE TABLE room_type_amenities (
    room_type_id UUID NOT NULL REFERENCES room_types(id) ON DELETE CASCADE,
    amenity_id UUID NOT NULL REFERENCES amenities(id),
    PRIMARY KEY(room_type_id, amenity_id)
);
CREATE INDEX idx_room_type_amenities_amenity ON room_type_amenities(amenity_id);
CREATE TABLE room_images (
    id UUID PRIMARY KEY,
    room_id UUID REFERENCES rooms(id) ON DELETE CASCADE,
    room_type_id UUID REFERENCES room_types(id) ON DELETE CASCADE,
    image_url VARCHAR(2048) NOT NULL CHECK (length(trim(image_url)) > 0),
    sort_order INTEGER NOT NULL DEFAULT 0 CHECK (sort_order >= 0),
    is_cover BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_image_owner CHECK ((room_id IS NULL) <> (room_type_id IS NULL))
);
CREATE INDEX idx_room_images_room_order ON room_images(room_id, sort_order);
CREATE INDEX idx_room_images_type_order ON room_images(room_type_id, sort_order);
CREATE UNIQUE INDEX uq_room_cover ON room_images(room_id) WHERE is_cover AND room_id IS NOT NULL;
CREATE UNIQUE INDEX uq_room_type_cover ON room_images(room_type_id) WHERE is_cover AND room_type_id IS NOT NULL;

CREATE TABLE room_access_codes (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES rooms(id),
    code_value VARCHAR(128) NOT NULL CHECK (length(code_value) > 0),
    valid_date DATE NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','REVOKED')),
    CONSTRAINT uq_room_access_code_date UNIQUE(room_id, valid_date)
);
