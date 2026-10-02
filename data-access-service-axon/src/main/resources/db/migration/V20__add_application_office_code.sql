ALTER TABLE application_current_state
    ADD COLUMN office_code VARCHAR(255);

ALTER TABLE application_list_index
    ADD COLUMN office_code VARCHAR(255);

CREATE INDEX idx_ali_office_code
    ON application_list_index (office_code);

