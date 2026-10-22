CREATE TABLE payments (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL REFERENCES bookings(id),
    payment_code VARCHAR(64) NOT NULL UNIQUE CHECK (length(trim(payment_code)) > 0),
    provider VARCHAR(32) NOT NULL CHECK (length(trim(provider)) > 0),
    amount NUMERIC(18,2) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'VND' CHECK (currency ~ '^[A-Z]{3}$'),
    status VARCHAR(24) NOT NULL CHECK (status IN
        ('PENDING','PAID','FAILED','CANCELLED','REFUNDED')),
    transaction_id VARCHAR(255),
    paid_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_payment_paid_timestamp CHECK (
        (status IN ('PAID', 'REFUNDED') AND paid_at IS NOT NULL)
        OR (status IN ('PENDING', 'FAILED', 'CANCELLED') AND paid_at IS NULL)
    )
);

CREATE INDEX idx_payments_booking ON payments(booking_id, created_at DESC, id DESC);
CREATE INDEX idx_payments_status_paid_at ON payments(status, paid_at);
CREATE UNIQUE INDEX uq_payments_booking_paid ON payments(booking_id) WHERE status = 'PAID';

CREATE TRIGGER payments_updated_at BEFORE UPDATE ON payments
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
