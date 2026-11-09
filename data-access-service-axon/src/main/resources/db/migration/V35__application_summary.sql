-- The list index becomes the application summary, read from event content by the
-- application-summary processor. It is rebuilt from the start of the event store.
ALTER TABLE application_list_index RENAME TO application_summary;

TRUNCATE application_summary;

ALTER TABLE application_summary
    ADD COLUMN category_of_law          VARCHAR(255),
    ADD COLUMN used_delegated_functions BOOLEAN,
    ADD COLUMN matter_types             JSONB,
    ADD COLUMN matter_type_codes        JSONB,
    ADD COLUMN linked_group_id          UUID;

CREATE INDEX idx_application_summary_linked_group ON application_summary (linked_group_id);

DELETE FROM token_entry WHERE processor_name = 'application-list-index-projection';
