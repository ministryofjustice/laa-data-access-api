CREATE TABLE IF NOT EXISTS linked_applications(
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    lead_application_id UUID NOT NULL,
    associated_application_id UUID NOT NULL,
    linked_at TIMESTAMPTZ NOT NULL DEFAULT (NOW() AT TIME ZONE 'UTC'),

    CONSTRAINT fk_linked_applications_lead_application_id FOREIGN KEY (lead_application_id)
    REFERENCES applications(id) ON DELETE CASCADE,

    CONSTRAINT fk_linked_applications_associated_application_id FOREIGN KEY (associated_application_id)
    REFERENCES applications(id) ON DELETE CASCADE
);