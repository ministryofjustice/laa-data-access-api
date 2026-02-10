ALTER TABLE applications
    ADD COLUMN decision_id UUID NULL;

ALTER TABLE applications
    ADD CONSTRAINT fk_applications_decisions_id FOREIGN KEY (decision_id)
        REFERENCES decisions(id) ON DELETE CASCADE;

ALTER TABLE decisions
DROP CONSTRAINT fk_decisions_applications_id;

ALTER TABLE decisions
DROP COLUMN application_id;