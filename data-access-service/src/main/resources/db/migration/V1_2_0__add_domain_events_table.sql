CREATE TABLE IF NOT EXISTS domain_events(
    ID UUID NOT NULL DEFAULT uuid_generate_v4(),
    application_id UUID,
    caseworker_id UUID NULL,
    type VARCHAR(50) NOT NULL,
    data JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL  DEFAULT(NOW() AT TIME ZONE 'UTC'),
    created_by VARCHAR(255) NOT NULL,
    CONSTRAINT domain_events_pkey PRIMARY KEY (ID),
    CONSTRAINT domain_events_application_id_fkey FOREIGN KEY(application_id) REFERENCES application(id),
    CONSTRAINT domain_events_caseworkers_id_fkey FOREIGN KEY(caseworker_id) REFERENCES caseworkers(id)
);