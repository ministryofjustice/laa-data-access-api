-- Decisions
CREATE TABLE IF NOT EXISTS decisions(
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    application_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT(NOW() AT TIME ZONE 'UTC'),
    modified_at TIMESTAMPTZ NOT NULL DEFAULT(NOW() AT TIME ZONE 'UTC'),
    created_by VARCHAR NULL,
    updated_by VARCHAR NULL,
    overall_decision VARCHAR NOT NULL,

    CONSTRAINT fk_decisions_applications_id FOREIGN KEY (application_id)
    REFERENCES applications(id) ON DELETE CASCADE
    );