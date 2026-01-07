-- merits_decisions
CREATE TABLE IF NOT EXISTS merits_decisions(
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    proceeding_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT(NOW() AT TIME ZONE 'UTC'),
    modified_at TIMESTAMPTZ NOT NULL DEFAULT(NOW() AT TIME ZONE 'UTC'),
    created_by VARCHAR NULL,
    updated_by VARCHAR NULL,
    decision VARCHAR NOT NULL,
    reason VARCHAR NULL,
    justification VARCHAR NULL
    );