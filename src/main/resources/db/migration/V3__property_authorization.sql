-- Minimal property identity is brought forward from Stage 2 so assignments have a real FK.
-- Stage 2 extends this table via new migrations, and adds inventory / CRUD.
CREATE TABLE properties (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL CHECK (length(trim(name)) > 0),
    slug VARCHAR(255) NOT NULL UNIQUE CHECK (length(trim(slug)) > 0),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TRIGGER properties_updated_at BEFORE UPDATE ON properties
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE user_properties (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    property_id UUID NOT NULL REFERENCES properties(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_properties UNIQUE (user_id, property_id)
);
CREATE INDEX idx_user_properties_property ON user_properties(property_id);
