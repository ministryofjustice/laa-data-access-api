ALTER TABLE prior_authority_history
    ADD COLUMN item_version BIGINT NOT NULL,
    DROP COLUMN event_data;