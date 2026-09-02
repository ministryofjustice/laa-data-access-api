ALTER TABLE prior_authority_current_state
    ADD COLUMN data_version BIGINT NOT NULL DEFAULT 0;