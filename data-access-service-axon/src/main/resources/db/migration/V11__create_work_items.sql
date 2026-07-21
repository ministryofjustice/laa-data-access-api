CREATE TABLE work_items (
    work_item_id           UUID PRIMARY KEY,
    work_type              VARCHAR(32) NOT NULL,
    application_id         UUID NOT NULL,
    prior_authority_id     UUID,
    laa_reference          VARCHAR(255),
    assigned_caseworker_id UUID,
    created_at             TIMESTAMPTZ NOT NULL,
    updated_at             TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_work_items_work_type ON work_items (work_type);
CREATE INDEX idx_work_items_assigned_caseworker_id ON work_items (assigned_caseworker_id);
