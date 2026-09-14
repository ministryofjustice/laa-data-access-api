-- Supports the per-page batch lookup of prior authorities by application ID
-- performed when serving GET /api/v0/applications.
CREATE INDEX idx_prior_authority_current_state_application_id
    ON prior_authority_current_state (application_id);
