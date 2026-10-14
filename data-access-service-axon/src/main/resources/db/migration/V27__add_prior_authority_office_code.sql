ALTER TABLE prior_authority_current_state
    ADD COLUMN office_code VARCHAR(255);

UPDATE prior_authority_current_state prior_authority
SET office_code = application.office_code
FROM application_current_state application
WHERE application.application_id = prior_authority.application_id;

CREATE INDEX idx_prior_authority_current_state_office_code
    ON prior_authority_current_state (office_code);

