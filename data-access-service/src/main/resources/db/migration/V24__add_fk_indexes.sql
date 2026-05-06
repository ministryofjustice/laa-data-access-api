-- =============================================================================
-- Add B-tree indexes on every FK column that was missing one.
-- ALL of these indexes are missing from the existing schema (V1–V23 contain
-- no CREATE INDEX statements).
-- =============================================================================

-- indexes needed for applications search

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_individuals_first_name_trgm
    ON individuals USING gin (lower(first_name) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_individuals_last_name_trgm
    ON individuals USING gin (lower(last_name) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_individuals_type
    ON individuals (individual_type);

CREATE INDEX IF NOT EXISTS idx_applications_status_submitted_created
    ON applications (status, submitted_at, created_at);

CREATE INDEX IF NOT EXISTS idx_applications_laa_reference_trgm
    ON applications USING gin (lower(laa_reference) gin_trgm_ops);

-- linked_individuals
CREATE INDEX IF NOT EXISTS idx_linked_individuals_application_id
    ON linked_individuals (application_id);

CREATE INDEX IF NOT EXISTS idx_linked_individuals_individual_id
    ON linked_individuals (individual_id);

-- linked_applications
CREATE INDEX IF NOT EXISTS idx_linked_applications_lead_application_id
    ON linked_applications (lead_application_id);

CREATE INDEX IF NOT EXISTS idx_linked_applications_associated_application_id
    ON linked_applications (associated_application_id);

-- applications
CREATE INDEX IF NOT EXISTS idx_applications_caseworker_id
    ON applications(caseworker_id);

CREATE INDEX IF NOT EXISTS idx_applications_apply_application_id
    ON applications(apply_application_id);

CREATE INDEX IF NOT EXISTS idx_applications_decision_id
    ON applications(decision_id);

-- proceedings
CREATE INDEX IF NOT EXISTS idx_proceedings_application_id
    ON proceedings(application_id);

CREATE INDEX IF NOT EXISTS idx_proceedings_merits_decision_id
    ON proceedings(merits_decision_id);


-- domain_events
CREATE INDEX IF NOT EXISTS idx_domain_events_application_id
    ON domain_events(application_id);

CREATE INDEX IF NOT EXISTS idx_domain_events_caseworker_id
    ON domain_events(caseworker_id);

-- certificates
-- application_id already has a UNIQUE constraint (V19) which implies a unique index — no extra index needed.

-- application_notes
CREATE INDEX IF NOT EXISTS idx_application_notes_application_id
    ON application_notes(application_id);

