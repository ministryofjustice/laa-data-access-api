-- Event content stored apart from its event so that it can be erased. One row per event that
-- carries content; an event with no row either had no content or has been erased. Rows are
-- written in the same transaction as their event.
CREATE TABLE event_content (
    event_id VARCHAR(255) PRIMARY KEY
        REFERENCES domain_event_entry (event_identifier)
        DEFERRABLE INITIALLY DEFERRED,
    tags TEXT[] NOT NULL,
    content JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX event_content_tags_idx ON event_content USING GIN (tags);

-- Content is never edited in place; erasure deletes the row.
CREATE FUNCTION reject_event_content_update()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Event content is immutable' USING ERRCODE = '23514';
END;
$$;

CREATE TRIGGER event_content_immutable
    BEFORE UPDATE ON event_content
    FOR EACH ROW EXECUTE FUNCTION reject_event_content_update();
