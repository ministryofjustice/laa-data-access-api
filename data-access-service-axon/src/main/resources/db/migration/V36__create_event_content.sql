-- Event content stored apart from its event so that it can be erased. One row per event that
-- carries content, written in the same transaction as its event. Every event whose type has
-- content fields therefore has a row, and a missing row is an error, never erased content.
CREATE TABLE event_content (
    event_id VARCHAR(255) PRIMARY KEY
        REFERENCES domain_event_entry (event_identifier)
        DEFERRABLE INITIALLY DEFERRED,
    tags TEXT[] NOT NULL,
    content JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    erased_at TIMESTAMPTZ NULL
);

CREATE INDEX event_content_tags_idx ON event_content USING GIN (tags);

-- Rows are never deleted and content is never edited in place. Hard erasure is the only
-- permitted update: it replaces the content (with an empty object or default content) and stamps
-- erased_at, once. The row is kept.
CREATE FUNCTION reject_event_content_update()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'UPDATE'
        AND OLD.erased_at IS NULL
        AND NEW.erased_at IS NOT NULL
        AND NEW.event_id = OLD.event_id
        AND NEW.tags = OLD.tags
        AND NEW.created_at = OLD.created_at THEN
        RETURN NEW;
    END IF;
    RAISE EXCEPTION 'Event content is immutable except for erasure' USING ERRCODE = '23514';
END;
$$;

CREATE TRIGGER event_content_immutable
    BEFORE UPDATE OR DELETE ON event_content
    FOR EACH ROW EXECUTE FUNCTION reject_event_content_update();
