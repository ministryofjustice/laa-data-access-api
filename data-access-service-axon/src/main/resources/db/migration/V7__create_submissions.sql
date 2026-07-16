CREATE TABLE submissions (
    event_id             UUID         PRIMARY KEY,
    apply_application_id UUID         NOT NULL,
    submission_type      VARCHAR(255) NOT NULL,
    causation_id         UUID,
    correlation_id       UUID,
    data                 JSONB        NOT NULL,
    created_at           TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_submissions_apply_application_id
    ON submissions (apply_application_id);

CREATE INDEX idx_submissions_submission_type
    ON submissions (submission_type);
