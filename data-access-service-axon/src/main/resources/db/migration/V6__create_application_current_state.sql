CREATE TABLE application_current_state (
    apply_application_id     UUID         PRIMARY KEY,
    status                   VARCHAR(255) NOT NULL,
    laa_reference            VARCHAR(255) NOT NULL,
    schema_version           INTEGER      NOT NULL,
    application_type         VARCHAR(255) NOT NULL,
    submitted_at             TIMESTAMPTZ  NOT NULL,
    office_code              VARCHAR(255),
    used_delegated_functions BOOLEAN,
    category_of_law          VARCHAR(255),
    matter_type              VARCHAR(255),
    created_at               TIMESTAMPTZ  NOT NULL
);
