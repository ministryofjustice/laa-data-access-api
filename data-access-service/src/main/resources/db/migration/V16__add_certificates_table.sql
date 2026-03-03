CREATE TABLE IF NOT EXISTS certificates(
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    application_id UUID NOT NULL,
    certificate_content JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT(NOW() AT TIME ZONE 'UTC'),
    created_by VARCHAR NOT NULL,
    modified_at TIMESTAMPTZ NOT NULL DEFAULT(NOW() AT TIME ZONE 'UTC'),
    updated_by VARCHAR NOT NULL,

    CONSTRAINT fk_certificates_applications_id FOREIGN KEY(application_id)
        REFERENCES applications(id) ON DELETE CASCADE
);
