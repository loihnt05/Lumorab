-- Foundation only: business tables arrive in their owning phases.
-- IDs: UUID. Times: TIMESTAMPTZ (Instant / UTC). Identifiers: snake_case.
-- Future tables should declare created_at and updated_at NOT NULL DEFAULT now().
-- Attach this trigger function to tables also updated outside JPA.
CREATE FUNCTION set_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = clock_timestamp();
    RETURN NEW;
END;
$$;
