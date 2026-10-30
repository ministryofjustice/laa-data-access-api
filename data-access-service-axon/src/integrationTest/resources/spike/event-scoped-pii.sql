CREATE SCHEMA IF NOT EXISTS spike;;

CREATE TABLE IF NOT EXISTS spike.event_content (
    event_id VARCHAR(255) PRIMARY KEY
        REFERENCES axon.domain_event_entry (event_identifier)
        DEFERRABLE INITIALLY DEFERRED,
    schema_version INTEGER NOT NULL,
    payload JSONB,
    erased_at TIMESTAMPTZ,
    CHECK (
        (erased_at IS NULL AND payload IS NOT NULL)
        OR (erased_at IS NOT NULL AND payload IS NULL)
    )
);;

CREATE OR REPLACE FUNCTION spike.protect_event_content()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.erased_at IS NULL
       AND NEW.erased_at IS NOT NULL
       AND NEW.payload IS NULL
       AND NEW.event_id = OLD.event_id
       AND NEW.schema_version = OLD.schema_version THEN
        RETURN NEW;
    END IF;
    RAISE EXCEPTION 'Event content is immutable' USING ERRCODE = '23514';
END;
$$;;

CREATE OR REPLACE TRIGGER protect_event_content
BEFORE UPDATE ON spike.event_content
FOR EACH ROW EXECUTE FUNCTION spike.protect_event_content();;

CREATE TABLE IF NOT EXISTS spike.application_view (
    application_id UUID PRIMARY KEY,
    stream_version BIGINT NOT NULL,
    payload JSONB NOT NULL
);;
