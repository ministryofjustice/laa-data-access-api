-- Enable UUID generation functions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Caseworkers
CREATE TABLE IF NOT EXISTS caseworkers(
    id UUID NOT NULL DEFAULT uuid_generate_v4(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT(NOW() AT TIME ZONE 'UTC'),
    modified_at TIMESTAMPTZ NOT NULL DEFAULT(NOW() AT TIME ZONE 'UTC'),
    username VARCHAR NOT NULL,
    CONSTRAINT caseworker_pkey PRIMARY KEY (id)
    );

-- Individuals
CREATE TABLE IF NOT EXISTS individuals(
    id UUID NOT NULL DEFAULT uuid_generate_v4(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT(NOW() AT TIME ZONE 'UTC'),
    modified_at TIMESTAMPTZ NOT NULL DEFAULT(NOW() AT TIME ZONE 'UTC'),
    first_name VARCHAR NOT NULL,
    last_name VARCHAR NOT NULL,
    individual_content JSONB NOT NULL,
    date_of_birth DATE NOT NULL,
    CONSTRAINT individuals_pkey PRIMARY KEY (id)
    );

-- Applications
CREATE TABLE IF NOT EXISTS applications(
    id UUID NOT NULL DEFAULT uuid_generate_v4(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT(NOW() AT TIME ZONE 'UTC'),
    modified_at TIMESTAMPTZ NOT NULL DEFAULT(NOW() AT TIME ZONE 'UTC'),
    status VARCHAR(20) NOT NULL,
    application_content JSONB NOT NULL,
    schema_version INTEGER,
    application_reference VARCHAR,
    caseworker_id UUID NULL,
    CONSTRAINT applications_pkey PRIMARY KEY (id),
    CONSTRAINT application_caseworkers_id_fkey FOREIGN KEY(caseworker_id) REFERENCES caseworkers(id)
    );

-- Linked Individuals
CREATE TABLE IF NOT EXISTS linked_individuals(
    linked_at TIMESTAMPTZ NOT NULL DEFAULT(NOW() AT TIME ZONE 'UTC'),
    application_id UUID NOT NULL,
    individual_id UUID NOT NULL,

    CONSTRAINT linked_individuals_pkey PRIMARY KEY (application_id, individual_id),
    CONSTRAINT linked_individuals_application_id_fkey FOREIGN KEY(application_id) REFERENCES applications(id),
    CONSTRAINT linked_individuals_individual_id_fkey FOREIGN KEY (individual_id) REFERENCES individuals(id)
    );

-- Domain Events
CREATE TABLE IF NOT EXISTS domain_events(
    id UUID NOT NULL DEFAULT uuid_generate_v4(),
    application_id UUID,
    caseworker_id UUID NULL,
    type VARCHAR(50) NOT NULL,
    data JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT(NOW() AT TIME ZONE 'UTC'),
    created_by VARCHAR(255) NOT NULL,
    CONSTRAINT domain_events_pkey PRIMARY KEY (id),
    CONSTRAINT domain_events_application_id_fkey FOREIGN KEY(application_id) REFERENCES applications(id),
    CONSTRAINT domain_events_caseworkers_id_fkey FOREIGN KEY(caseworker_id) REFERENCES caseworkers(id)
    );