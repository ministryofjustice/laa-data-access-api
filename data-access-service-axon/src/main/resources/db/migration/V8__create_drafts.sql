-- Deletable, mutable store for an application draft body. Keyed by the client-supplied (interim)
-- application id, overwritten in place as the draft is edited, and sealed into an immutable
-- submissions row on submit. It is the system-of-record for the draft body and is NOT rebuildable
-- by replaying events (the draft events are PII-free pointers).
CREATE TABLE drafts (
    apply_application_id UUID         PRIMARY KEY,
    content              JSONB        NOT NULL,
    created_at           TIMESTAMPTZ  NOT NULL,
    updated_at           TIMESTAMPTZ  NOT NULL
);
