-- Structurally identical to submissions (see V7), but drafts are mutable: rows are
-- updated in place as a draft is edited before submission.
CREATE TABLE drafts (
    event_id             UUID         PRIMARY KEY,
    apply_application_id UUID         NOT NULL,
    draft_type      VARCHAR(255) NOT NULL,
    causation_id         UUID,
    correlation_id       UUID,
    data                 JSONB        NOT NULL,
    created_at           TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_drafts_apply_application_id
    ON drafts (apply_application_id);

CREATE INDEX idx_drafts_draft_type
    ON drafts (draft_type);
