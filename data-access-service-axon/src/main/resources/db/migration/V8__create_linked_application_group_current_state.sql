CREATE TABLE linked_application_group_current_state (
    group_id            UUID PRIMARY KEY,
    lead_application_id UUID NOT NULL,
    member_ids          JSONB NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL,
    modified_at         TIMESTAMPTZ NOT NULL
);
