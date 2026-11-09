-- Hard erasure keeps the content row: it replaces the content (with an empty object or default
-- content) and stamps erased_at. Every event whose type has content fields therefore has a row,
-- and a missing row is an error rather than erased content. Rows are never deleted, and erasure
-- is the only permitted update.
ALTER TABLE event_content ADD COLUMN erased_at TIMESTAMPTZ;

CREATE OR REPLACE FUNCTION reject_event_content_update()
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

DROP TRIGGER event_content_immutable ON event_content;

CREATE TRIGGER event_content_immutable
    BEFORE UPDATE OR DELETE ON event_content
    FOR EACH ROW EXECUTE FUNCTION reject_event_content_update();
