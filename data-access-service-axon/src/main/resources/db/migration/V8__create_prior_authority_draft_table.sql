CREATE TABLE prior_authority_draft (
    prior_authority_id UUID        NOT NULL,
    application_id     UUID        NOT NULL,
    payload             JSONB       NOT NULL,
    payload_hash        VARCHAR(64) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (prior_authority_id)
);

CREATE INDEX idx_prior_authority_draft_application_id
    ON prior_authority_draft (application_id);
