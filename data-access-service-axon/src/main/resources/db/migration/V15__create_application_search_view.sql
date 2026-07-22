-- Application Search View
-- Flattened, denormalized projection for efficient filtering, sorting, and pagination
-- One row per application with searchable fields

CREATE TABLE application_search_view (
    application_id          UUID PRIMARY KEY,
    stream_version          BIGINT,
    status                  VARCHAR(255),
    laa_reference           VARCHAR(255),
    schema_version          INTEGER,
    client_first_name       VARCHAR(255),
    client_last_name        VARCHAR(255),
    client_date_of_birth    DATE,
    caseworker_id           VARCHAR(255),
    matter_type             VARCHAR(255),
    category_of_law         VARCHAR(255),
    is_auto_granted         BOOLEAN,
    submitted_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ,
    modified_at             TIMESTAMPTZ,
    lead_application_id     UUID,
    is_lead                 BOOLEAN,
    projection_position     BIGINT
);

-- Search view indexes for filtering, sorting, and pagination
CREATE INDEX idx_application_search_view_status
  ON application_search_view(status);

CREATE INDEX idx_application_search_view_laa_reference
  ON application_search_view(laa_reference);

CREATE INDEX idx_application_search_view_caseworker_id
  ON application_search_view(caseworker_id);

CREATE INDEX idx_application_search_view_matter_type
  ON application_search_view(matter_type);

CREATE INDEX idx_application_search_view_is_auto_granted
  ON application_search_view(is_auto_granted);

-- Indexes for sorting with deterministic tie-breaker
CREATE INDEX idx_application_search_view_submitted_at
  ON application_search_view(submitted_at, application_id);

CREATE INDEX idx_application_search_view_modified_at
  ON application_search_view(modified_at, application_id);

-- Index for client name search and sort
CREATE INDEX idx_application_search_view_client_name
  ON application_search_view(client_last_name, client_first_name, client_date_of_birth);

-- Index for linked application lookups
CREATE INDEX idx_application_search_view_lead_application_id
  ON application_search_view(lead_application_id);

-- Application Link Search View
-- One row per membership in a linked-application group
-- Composite primary key ensures one membership per (lead_application_id, application_id) pair

CREATE TABLE application_link_search_view (
    lead_application_id     UUID NOT NULL,
    application_id          UUID NOT NULL,
    stream_version          BIGINT,
    projection_position     BIGINT,
    PRIMARY KEY (lead_application_id, application_id)
);

-- Indexes for group membership lookups
CREATE INDEX idx_application_link_search_view_application_id
  ON application_link_search_view(application_id);

CREATE INDEX idx_application_link_search_view_lead_application_id
  ON application_link_search_view(lead_application_id);

