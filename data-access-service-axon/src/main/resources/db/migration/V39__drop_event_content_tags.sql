-- Event content is now found by event id after the events are read, so the stream tags copied
-- onto each row are no longer needed. The trigger function is replaced first: PL/pgSQL bodies are
-- not dependency-tracked, so the old body would fail at runtime once the tags column is gone.
-- Hard erasure stays the only permitted update: it replaces the content with an empty object and
-- stamps erased_at, once. The row is kept.
CREATE OR REPLACE FUNCTION reject_event_content_update()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'UPDATE'
        AND OLD.erased_at IS NULL
        AND NEW.erased_at IS NOT NULL
        AND NEW.event_id = OLD.event_id
        AND NEW.created_at = OLD.created_at THEN
        RETURN NEW;
    END IF;
    RAISE EXCEPTION 'Event content is immutable except for erasure' USING ERRCODE = '23514';
END;
$$;

DROP INDEX event_content_tags_idx;

ALTER TABLE event_content DROP COLUMN tags;
