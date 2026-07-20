CREATE TABLE pii_records (
    pii_ref UUID PRIMARY KEY,
    application_id UUID NOT NULL,
    encrypted_payload BYTEA NOT NULL,
    pii_valid_from TIMESTAMPTZ NOT NULL DEFAULT now(),
    pii_valid_until TIMESTAMPTZ,
    pii_status VARCHAR(20) NOT NULL DEFAULT 'PRESENT',
    created_by VARCHAR(255)
);

CREATE INDEX idx_pii_application ON pii_records(application_id);
CREATE INDEX idx_pii_temporal ON pii_records(application_id, pii_valid_from, pii_valid_until);

CREATE TABLE pii_redaction_audit (
    id BIGSERIAL PRIMARY KEY,
    application_id UUID NOT NULL,
    reason TEXT,
    actor VARCHAR(255),
    redacted_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_application ON pii_redaction_audit(application_id);
