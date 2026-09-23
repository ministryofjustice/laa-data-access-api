ALTER TABLE prior_authority_current_state
    ADD COLUMN modified_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
