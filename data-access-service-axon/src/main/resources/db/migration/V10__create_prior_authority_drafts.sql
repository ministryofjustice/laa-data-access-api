-- Deletable, mutable store for prior authority draft bodies. A prior authority draft is
-- edited in place (latest-version wins), so the row is keyed by prior_authority_id and
-- upserted, unlike the immutable submissions store. The pointer events carry only the
-- prior_authority_id; the body here is deletable independently of the event stream and is
-- NOT rebuildable by replaying events.
CREATE TABLE prior_authority_drafts (
    prior_authority_id   UUID         PRIMARY KEY,
    apply_application_id UUID         NOT NULL,
    content              JSONB        NOT NULL,
    created_at           TIMESTAMPTZ  NOT NULL,
    updated_at           TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_prior_authority_drafts_apply_application_id
    ON prior_authority_drafts (apply_application_id);
