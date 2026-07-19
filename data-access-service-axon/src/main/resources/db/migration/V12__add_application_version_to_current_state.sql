ALTER TABLE application_current_state
    ADD COLUMN application_version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE application_current_state
    ALTER COLUMN application_version DROP DEFAULT;
