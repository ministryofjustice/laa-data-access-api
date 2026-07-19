ALTER TABLE application_current_state
    ADD COLUMN caseworker_id UUID;

ALTER TABLE application_current_state
    ADD CONSTRAINT fk_application_current_state_caseworker
        FOREIGN KEY (caseworker_id) REFERENCES caseworkers(id);
