ALTER TABLE application_data
    ADD COLUMN pii_ref UUID,
    ADD COLUMN pii_valid_from TIMESTAMPTZ,
    ADD COLUMN pii_valid_until TIMESTAMPTZ,
    ADD COLUMN pii_status VARCHAR(20) NOT NULL DEFAULT 'PRESENT',
    ADD COLUMN redacted_at TIMESTAMPTZ,
    ADD COLUMN redacted_by VARCHAR(255),
    ADD COLUMN redaction_reason TEXT;

CREATE INDEX idx_pii_ref ON application_data(pii_ref);
CREATE INDEX idx_application_data_pii_temporal
    ON application_data(application_id, pii_valid_from, pii_valid_until);
