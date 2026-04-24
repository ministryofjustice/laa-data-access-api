-- =============================================================================
-- Performance indexes for application summary search queries.
--
-- At 100k+ rows the summary search queries degrade significantly because
-- every filter, join, and sort column lacks an index. Postgres falls back
-- to sequential scans on applications, individuals, linked_individuals,
-- and linked_applications.
-- =============================================================================

-- Trigram extension for LIKE '%...%' searches
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Individual name searches (leading-wildcard LIKE needs trigram, not btree)
CREATE INDEX IF NOT EXISTS idx_individuals_first_name_trgm
    ON individuals USING gin (lower(first_name) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_individuals_last_name_trgm
    ON individuals USING gin (lower(last_name) gin_trgm_ops);

-- Individual type filter (equality predicate)
CREATE INDEX IF NOT EXISTS idx_individuals_type
    ON individuals (individual_type);

-- Application status filter + sort columns
CREATE INDEX IF NOT EXISTS idx_applications_status_submitted_created
    ON applications (status, submitted_at, created_at);

-- linked_individuals join on individual_id (PK only covers application_id first)
CREATE INDEX IF NOT EXISTS idx_linked_individuals_individual_id
    ON linked_individuals (individual_id);

-- linked_applications FK columns (Postgres does not auto-index FKs)
CREATE INDEX IF NOT EXISTS idx_linked_applications_lead_id
    ON linked_applications (lead_application_id);

CREATE INDEX IF NOT EXISTS idx_linked_applications_associated_id
    ON linked_applications (associated_application_id);

-- LAA reference LIKE search
CREATE INDEX IF NOT EXISTS idx_applications_laa_reference_trgm
    ON applications USING gin (lower(laa_reference) gin_trgm_ops);

-- Caseworker filter join
CREATE INDEX IF NOT EXISTS idx_applications_caseworker_id
    ON applications (caseworker_id);
