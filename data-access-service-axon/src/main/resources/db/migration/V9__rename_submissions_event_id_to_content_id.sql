-- The submissions table is now written by the application layer before the pointer event exists,
-- keyed by a minted content_id that the ApplicationSubmittedEvent references (rather than the
-- producing event id). Rename the primary key column accordingly.
ALTER TABLE submissions RENAME COLUMN event_id TO content_id;
