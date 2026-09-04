ALTER TABLE prior_authority_data
    RENAME COLUMN submission_id TO prior_authority_id;

ALTER TABLE prior_authority_current_state
    RENAME COLUMN submission_id TO prior_authority_id;
