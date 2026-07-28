CREATE TABLE application_history (
    event_id VARCHAR(255) PRIMARY KEY,
    application_id UUID NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    request_payload JSONB NOT NULL,
    service_name VARCHAR(255),
    occurred_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX application_history_application_id_idx ON application_history (application_id);
