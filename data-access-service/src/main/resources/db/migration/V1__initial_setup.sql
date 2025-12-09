-- Enable UUID generation functions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Caseworkers
CREATE TABLE IF NOT EXISTS caseworkers(
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT(NOW() AT TIME ZONE 'UTC'),
    modified_at TIMESTAMPTZ NOT NULL DEFAULT(NOW() AT TIME ZONE 'UTC'),
    username VARCHAR NOT NULL
    );

-- Individuals
CREATE TABLE IF NOT EXISTS individuals(
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT(NOW() AT TIME ZONE 'UTC'),
    modified_at TIMESTAMPTZ NOT NULL DEFAULT(NOW() AT TIME ZONE 'UTC'),
    created_by VARCHAR NULL,
    updated_by VARCHAR NULL,

    first_name VARCHAR NOT NULL,
    last_name VARCHAR NOT NULL,
    individual_content JSONB NOT NULL,
    date_of_birth DATE NOT NULL
    );

-- Applications
CREATE TABLE IF NOT EXISTS applications(
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT(NOW() AT TIME ZONE 'UTC'),
    modified_at TIMESTAMPTZ NOT NULL DEFAULT(NOW() AT TIME ZONE 'UTC'),
    created_by VARCHAR NULL,
    updated_by VARCHAR NULL,

    status VARCHAR NOT NULL,
    application_content JSONB NOT NULL,
    schema_version INTEGER,
    application_reference VARCHAR,
    caseworker_id UUID NULL,

    CONSTRAINT fk_application_caseworker FOREIGN KEY(caseworker_id)
        REFERENCES caseworkers(id)
    );

-- Linked Individuals (M2M)
CREATE TABLE IF NOT EXISTS linked_individuals(
    linked_at TIMESTAMPTZ NOT NULL DEFAULT(NOW() AT TIME ZONE 'UTC'),
    application_id UUID NOT NULL,
    individual_id UUID NOT NULL,

    PRIMARY KEY (application_id, individual_id),

    CONSTRAINT fk_linked_individuals_application FOREIGN KEY (application_id)
        REFERENCES applications(id) ON DELETE CASCADE,

    CONSTRAINT fk_linked_individuals_individual FOREIGN KEY (individual_id)
        REFERENCES individuals(id)
    );

-- Domain Events
CREATE TABLE IF NOT EXISTS domain_events(
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    application_id UUID,
    caseworker_id UUID NULL,
    type VARCHAR NOT NULL,
    data JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT(NOW() AT TIME ZONE 'UTC'),
    created_by VARCHAR NULL,

    CONSTRAINT domain_events_application_id_fkey FOREIGN KEY(application_id)
        REFERENCES applications(id),

    CONSTRAINT domain_events_caseworkers_id_fkey FOREIGN KEY(caseworker_id)
        REFERENCES caseworkers(id)
    );