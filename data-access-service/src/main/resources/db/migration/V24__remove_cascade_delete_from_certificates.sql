ALTER TABLE certificates DROP CONSTRAINT fk_certificates_applications_id;

ALTER TABLE certificates
    ADD CONSTRAINT fk_certificates_applications_id
        FOREIGN KEY (application_id) REFERENCES applications (id);
