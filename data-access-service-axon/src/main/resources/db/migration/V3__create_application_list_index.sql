CREATE TABLE application_list_index (
    application_id       UUID         NOT NULL,
    status               VARCHAR(255) NOT NULL,
    laa_reference        VARCHAR(255),
    caseworker_id        UUID,
    matter_type          VARCHAR(255),
    is_auto_granted      BOOLEAN,
    submitted_at         TIMESTAMPTZ,
    lead_application_id  UUID,
    client_first_name    VARCHAR(255),
    client_last_name     VARCHAR(255),
    client_date_of_birth DATE,
    stream_version       BIGINT       NOT NULL DEFAULT 0,
    projection_position  BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (application_id)
);

-- Equality filter indexes
CREATE INDEX idx_ali_status        ON application_list_index (status);
CREATE INDEX idx_ali_matter_type   ON application_list_index (matter_type);
CREATE INDEX idx_ali_laa_reference ON application_list_index (laa_reference);
CREATE INDEX idx_ali_caseworker    ON application_list_index (caseworker_id);

-- Case-insensitive client name filter indexes
CREATE INDEX idx_ali_client_last_name  ON application_list_index (lower(client_last_name));
CREATE INDEX idx_ali_client_first_name ON application_list_index (lower(client_first_name));

-- Exact DOB equality filter
CREATE INDEX idx_ali_client_dob ON application_list_index (client_date_of_birth);

-- Sort index (most common: newest first)
CREATE INDEX idx_ali_submitted_at ON application_list_index (submitted_at DESC NULLS LAST);

-- Composite for the caseworker worklist: filter + sort in a single scan
CREATE INDEX idx_ali_caseworker_submitted
    ON application_list_index (caseworker_id, submitted_at DESC NULLS LAST);

