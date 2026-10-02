CREATE TABLE application_draft (
    application_id UUID        NOT NULL,
    payload        JSONB       NOT NULL,
    payload_hash   VARCHAR(64) NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (application_id)
);
