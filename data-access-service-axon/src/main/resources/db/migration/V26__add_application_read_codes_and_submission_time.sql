ALTER TABLE application_current_state
    ADD COLUMN submitted_at TIMESTAMPTZ;

ALTER TABLE application_list_index
    ADD COLUMN category_of_law_code VARCHAR(255),
    ADD COLUMN matter_type_code VARCHAR(255);

CREATE INDEX idx_ali_matter_type_code ON application_list_index (matter_type_code);

ALTER TABLE work_list_item
    ADD COLUMN category_of_law_code VARCHAR(255),
    ADD COLUMN matter_type_codes JSONB;

ALTER TABLE work_list_item
    RENAME COLUMN submitted_at TO ready_at;
