-- Proceedings
CREATE TABLE IF NOT EXISTS proceedings (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    application_id UUID NOT NULL,
    apply_proceeding_id UUID NOT NULL,
    description VARCHAR NOT NULL,
    is_lead BOOLEAN NOT NULL,
    proceeding_content JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT (NOW() AT TIME ZONE 'UTC'),
    modified_at TIMESTAMPTZ NOT NULL DEFAULT (NOW() AT TIME ZONE 'UTC'),
    created_by VARCHAR NULL,
    updated_by VARCHAR NULL,

    CONSTRAINT fk_proceedings_applications_id FOREIGN KEY (application_id)
    REFERENCES applications(id) ON DELETE CASCADE
);


ALTER TABLE merits_decisions
    ADD CONSTRAINT fk_merits_decisions_proceedings_id FOREIGN KEY (proceeding_id)
            REFERENCES proceedings(id) ON DELETE CASCADE;
