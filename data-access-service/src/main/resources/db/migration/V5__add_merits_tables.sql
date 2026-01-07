-- merits_decisions table
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

-- linked_merits_decisions table
CREATE TABLE IF NOT EXISTS linked_merits_decisions(
    decisions_id UUID NOT NULL,
    merits_decisions_id UUID NOT NULL,
    linked_at TIMESTAMPTZ NOT NULL DEFAULT(NOW() AT TIME ZONE 'UTC'),

    PRIMARY KEY (decisions_id, merits_decisions_id),

    CONSTRAINT fk_linked_merits_decisions_decisions_id FOREIGN KEY (decisions_id)
    REFERENCES decisions(id) ON DELETE CASCADE,

    CONSTRAINT fk_linked_merits_decisions_merits_decisions_id FOREIGN KEY (merits_decisions_id)
    REFERENCES merits_decisions(id) ON DELETE CASCADE
    );
