-- Enable UUID generation
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

---------------------------------------------------------------------
-- APPLICATION
---------------------------------------------------------------------
CREATE TABLE application (
    id UUID PRIMARY KEY,
    application_reference VARCHAR,
    created_at TIMESTAMPTZ NOT NULL DEFAULT (NOW() AT TIME ZONE 'UTC'),
    modified_at TIMESTAMPTZ NOT NULL DEFAULT (NOW() AT TIME ZONE 'UTC'),

    status VARCHAR(20) NOT NULL,
    application_content JSONB NOT NULL,
    schema_version INTEGER
);

---------------------------------------------------------------------
-- INDIVIDUAL
---------------------------------------------------------------------
CREATE TABLE individual (
    id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT (NOW() AT TIME ZONE 'UTC'),
    modified_at TIMESTAMPTZ NOT NULL DEFAULT (NOW() AT TIME ZONE 'UTC'),

    first_name VARCHAR NOT NULL,
    last_name VARCHAR NOT NULL,
    date_of_birth DATE NOT NULL,
    individual_content JSONB NOT NULL
);

---------------------------------------------------------------------
-- MANY-TO-MANY
---------------------------------------------------------------------
CREATE TABLE linked_individual (
    application_id UUID NOT NULL,
    individual_id  UUID NOT NULL,

    PRIMARY KEY (application_id, individual_id),

    CONSTRAINT fk_linked_individual_application FOREIGN KEY (application_id)
       REFERENCES application(id) ON DELETE CASCADE,

    CONSTRAINT fk_linked_individual_individual FOREIGN KEY (individual_id)
       REFERENCES individual(id) ON DELETE CASCADE
);