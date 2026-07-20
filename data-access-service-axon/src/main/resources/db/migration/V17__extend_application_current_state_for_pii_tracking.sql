ALTER TABLE application_current_state
    ADD COLUMN current_pii_ref UUID,
    ADD COLUMN pii_status VARCHAR(20);

CREATE INDEX idx_application_current_pii_ref ON application_current_state(current_pii_ref);
