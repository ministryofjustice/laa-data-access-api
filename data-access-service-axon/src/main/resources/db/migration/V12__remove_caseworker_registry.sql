ALTER TABLE application_current_state
    DROP CONSTRAINT IF EXISTS fk_application_current_state_caseworker;

ALTER TABLE application_history
    ADD COLUMN caseworker_id UUID;

DROP TABLE IF EXISTS caseworkers;

