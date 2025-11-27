-- Enable UUID generation
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

---------------------------------------------------------------------
-- INDIVIDUALS
---------------------------------------------------------------------
CREATE TABLE individuals (
     id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
     created_at TIMESTAMPTZ NOT NULL DEFAULT (NOW() AT TIME ZONE 'UTC'),
     modified_at TIMESTAMPTZ NOT NULL DEFAULT (NOW() AT TIME ZONE 'UTC'),

     first_name VARCHAR NOT NULL,
     last_name VARCHAR NOT NULL,
     date_of_birth DATE NOT NULL,
     individual_content JSONB NOT NULL
);

---------------------------------------------------------------------
-- CASEWORKERS
---------------------------------------------------------------------
CREATE TABLE caseworkers(
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT(NOW() AT TIME ZONE 'UTC'),
    modified_at TIMESTAMPTZ NOT NULL DEFAULT(NOW() AT TIME ZONE 'UTC'),

    username VARCHAR NOT NULL
);

---------------------------------------------------------------------
-- APPLICATIONS
---------------------------------------------------------------------
CREATE TABLE applications (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT (NOW() AT TIME ZONE 'UTC'),
    modified_at TIMESTAMPTZ NOT NULL DEFAULT (NOW() AT TIME ZONE 'UTC'),

    caseworker_id UUID NULL,
    application_reference VARCHAR,
    status VARCHAR(20) NOT NULL,
    application_content JSONB NOT NULL,
    schema_version INTEGER,

    CONSTRAINT fk_application_caseworker FOREIGN KEY (caseworker_id)
        REFERENCES caseworkers(id)
);

---------------------------------------------------------------------
-- MANY-TO-MANY
---------------------------------------------------------------------
CREATE TABLE linked_individuals(
    application_id UUID NOT NULL,
    individual_id  UUID NOT NULL,

    PRIMARY KEY (application_id, individual_id),

    CONSTRAINT fk_linked_individual_application FOREIGN KEY (application_id)
        REFERENCES applications(id) ON DELETE CASCADE,

    CONSTRAINT fk_linked_individual_individual FOREIGN KEY (individual_id)
        REFERENCES individuals(id) ON DELETE CASCADE
);