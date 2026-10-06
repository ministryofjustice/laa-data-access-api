ALTER TABLE application_history
    ADD COLUMN data_version BIGINT,
    DROP COLUMN request_payload;
