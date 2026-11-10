ALTER TABLE application_current_state
    ADD COLUMN assignment_version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE prior_authority_current_state
    ADD COLUMN assignment_version BIGINT NOT NULL DEFAULT 0;
