ALTER TABLE application_draft
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE prior_authority_draft
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;