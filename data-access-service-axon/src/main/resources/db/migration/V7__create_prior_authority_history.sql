CREATE TABLE prior_authority_history (
    event_id              VARCHAR(255)   PRIMARY KEY,
    application_id        UUID           NOT NULL,
    submission_id         UUID           NOT NULL,
    prior_authority_type  VARCHAR(50)    NOT NULL,
    event_type            VARCHAR(255)   NOT NULL,
    event_data            JSONB          NOT NULL,
    service_name          VARCHAR(255),
    occurred_at           TIMESTAMPTZ    NOT NULL
);

CREATE INDEX prior_authority_history_application_id_occurred_at_idx
    ON prior_authority_history (application_id, occurred_at);
