ALTER TABLE applications
    ADD is_lead BOOLEAN;

ALTER TABLE decisions
    ADD CONSTRAINT fk_decisions_application
        FOREIGN KEY (application_id) REFERENCES applications(id)
            ON DELETE CASCADE;

ALTER TABLE applications
ALTER
COLUMN status TYPE VARCHAR(255) USING (status::VARCHAR(255));