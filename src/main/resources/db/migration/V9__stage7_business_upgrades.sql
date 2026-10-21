ALTER TABLE bookings
    ADD COLUMN cancelled_at TIMESTAMPTZ,
    ADD COLUMN cancelled_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN cancellation_reason VARCHAR(1000),
    ADD COLUMN check_in_qr_token VARCHAR(128) UNIQUE;

ALTER TABLE payments
    ADD COLUMN qr_payload VARCHAR(2000),
    ADD COLUMN refunded_at TIMESTAMPTZ;

CREATE TABLE refunds (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL UNIQUE REFERENCES bookings(id) ON DELETE CASCADE,
    payment_id UUID REFERENCES payments(id) ON DELETE CASCADE,
    refund_code VARCHAR(80) NOT NULL UNIQUE,
    refund_amount NUMERIC(18,2) NOT NULL CHECK (refund_amount >= 0),
    fee_amount NUMERIC(18,2) NOT NULL CHECK (fee_amount >= 0),
    refund_percentage INTEGER NOT NULL CHECK (refund_percentage IN (0, 50, 100)),
    currency VARCHAR(3) NOT NULL DEFAULT 'VND' CHECK (currency ~ '^[A-Z]{3}$'),
    policy_tier VARCHAR(40) NOT NULL,
    status VARCHAR(24) NOT NULL CHECK (status IN ('PENDING', 'REFUNDED', 'NO_REFUND', 'FAILED')),
    reason VARCHAR(1000),
    processed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_refunds_booking ON refunds(booking_id);
CREATE INDEX idx_refunds_payment ON refunds(payment_id);

CREATE TABLE payment_webhook_events (
    id UUID PRIMARY KEY,
    provider VARCHAR(32) NOT NULL CHECK (length(trim(provider)) > 0),
    event_id VARCHAR(128) NOT NULL CHECK (length(trim(event_id)) > 0),
    payment_id UUID REFERENCES payments(id) ON DELETE CASCADE,
    booking_id UUID REFERENCES bookings(id) ON DELETE CASCADE,
    event_type VARCHAR(32) NOT NULL CHECK (length(trim(event_type)) > 0),
    outcome VARCHAR(32) NOT NULL CHECK (length(trim(outcome)) > 0),
    payload_summary VARCHAR(500),
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_webhook_provider_event UNIQUE (provider, event_id)
);

CREATE INDEX idx_webhook_events_payment ON payment_webhook_events(payment_id);

CREATE TABLE email_notifications (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(128) NOT NULL UNIQUE CHECK (length(trim(idempotency_key)) > 0),
    booking_id UUID REFERENCES bookings(id) ON DELETE CASCADE,
    recipient_email VARCHAR(320) NOT NULL CHECK (length(trim(recipient_email)) > 0),
    template VARCHAR(64) NOT NULL CHECK (length(trim(template)) > 0),
    subject VARCHAR(500) NOT NULL CHECK (length(trim(subject)) > 0),
    payload VARCHAR(4000) CHECK (
        payload IS NULL
        OR (position('codeValue' in payload) = 0 AND position('code_value' in payload) = 0)
    ),
    status VARCHAR(24) NOT NULL CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at TIMESTAMPTZ
);

CREATE INDEX idx_email_notifications_booking ON email_notifications(booking_id, created_at DESC);
CREATE INDEX idx_email_notifications_created ON email_notifications(created_at DESC, id DESC);
