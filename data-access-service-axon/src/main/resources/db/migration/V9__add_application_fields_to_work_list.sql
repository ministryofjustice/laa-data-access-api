ALTER TABLE work_list_item
    ADD COLUMN used_delegated_functions BOOLEAN,
    ADD COLUMN category_of_law VARCHAR(255),
    ADD COLUMN matter_types JSONB,
    ADD COLUMN application_status VARCHAR(64);

