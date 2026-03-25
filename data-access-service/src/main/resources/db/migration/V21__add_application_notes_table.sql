CREATE TABLE IF NOT EXISTS application_notes(
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    application_id UUID NOT NULL,
    notes VARCHAR NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT(NOW() AT TIME ZONE 'UTC'),
    created_by VARCHAR NOT NULL,

    CONSTRAINT fk_application_notes_applications_id FOREIGN KEY(application_id)
    REFERENCES applications(id) ON DELETE CASCADE
    );
