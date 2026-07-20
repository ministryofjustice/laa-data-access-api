DROP TABLE IF EXISTS axon.pii_records;

CREATE TABLE axon.pii_records (
    fragment_ref    UUID        PRIMARY KEY,
    application_id  UUID        NOT NULL,
    section_name    TEXT        NOT NULL,
    content_hash    TEXT        NOT NULL,
    encrypted_payload BYTEA     NOT NULL,
    saved_at        TIMESTAMPTZ NOT NULL,
    pii_status      TEXT        NOT NULL DEFAULT 'PRESENT'
);

CREATE INDEX ON axon.pii_records (application_id, section_name);
