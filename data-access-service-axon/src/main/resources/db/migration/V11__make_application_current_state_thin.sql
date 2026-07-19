ALTER TABLE application_current_state
    ADD COLUMN application_data_version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE application_current_state
    ALTER COLUMN application_data_version DROP DEFAULT,
    DROP COLUMN laa_reference,
    DROP COLUMN application_content,
    DROP COLUMN individuals,
    DROP COLUMN submitted_at,
    DROP COLUMN office_code,
    DROP COLUMN used_delegated_functions,
    DROP COLUMN category_of_law,
    DROP COLUMN matter_type,
    DROP COLUMN proceedings;
