ALTER TABLE prior_authority_current_state
    ADD COLUMN modified_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

UPDATE prior_authority_current_state
SET modified_at = created_at
WHERE modified_at IS NULL OR modified_at <> created_at;