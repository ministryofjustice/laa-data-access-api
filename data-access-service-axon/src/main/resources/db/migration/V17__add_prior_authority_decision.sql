ALTER TABLE prior_authority_current_state
    ADD COLUMN decision VARCHAR(20),
    ADD COLUMN prior_authority_type VARCHAR(32);
