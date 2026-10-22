ALTER TABLE bookings
    ADD COLUMN checked_in_at TIMESTAMPTZ,
    ADD COLUMN checked_out_at TIMESTAMPTZ,
    ADD COLUMN completed_at TIMESTAMPTZ;

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    actor_user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    action VARCHAR(64) NOT NULL CHECK (length(trim(action)) > 0),
    entity_type VARCHAR(64) NOT NULL CHECK (length(trim(entity_type)) > 0),
    entity_id VARCHAR(128) NOT NULL CHECK (length(trim(entity_id)) > 0),
    metadata VARCHAR(4000) CHECK (
        metadata IS NULL
        OR (position('codeValue' in metadata) = 0 AND position('code_value' in metadata) = 0)
    ),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_logs_created ON audit_logs(created_at DESC, id DESC);
CREATE INDEX idx_audit_logs_actor ON audit_logs(actor_user_id, created_at DESC);
CREATE INDEX idx_audit_logs_entity ON audit_logs(entity_type, entity_id, created_at DESC);
