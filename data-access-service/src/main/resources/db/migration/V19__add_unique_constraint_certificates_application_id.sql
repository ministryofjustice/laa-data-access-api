ALTER TABLE certificates
    ADD CONSTRAINT uq_certificates_application_id UNIQUE (application_id);
