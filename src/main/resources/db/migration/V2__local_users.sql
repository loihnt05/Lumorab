CREATE TABLE users (
    id UUID PRIMARY KEY,
    clerk_user_id VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(320),
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    phone VARCHAR(32),
    role VARCHAR(16) NOT NULL DEFAULT 'USER' CHECK (role IN ('USER', 'MANAGER', 'ADMIN')),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DISABLED')),
    profile_synced_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TRIGGER users_updated_at BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
