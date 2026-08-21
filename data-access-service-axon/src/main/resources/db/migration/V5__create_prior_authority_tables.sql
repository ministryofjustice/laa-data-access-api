CREATE TABLE prior_authority_data (
    submission_id  UUID        NOT NULL,
    data_version   BIGINT      NOT NULL,
    application_id UUID        NOT NULL,
    payload        JSONB       NOT NULL,
    payload_hash   VARCHAR(64) NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (submission_id, data_version)
);

CREATE INDEX idx_prior_authority_data_application_id
    ON prior_authority_data (application_id);

CREATE FUNCTION reject_prior_authority_data_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'prior_authority_data is append-only; % is prohibited', TG_OP;
END;
$$;

CREATE TRIGGER prior_authority_data_no_update
BEFORE UPDATE ON prior_authority_data
FOR EACH ROW EXECUTE FUNCTION reject_prior_authority_data_mutation();

CREATE TRIGGER prior_authority_data_no_direct_delete
BEFORE DELETE ON prior_authority_data
FOR EACH ROW
WHEN (current_setting('application.retention_delete', true) IS DISTINCT FROM 'enabled')
EXECUTE FUNCTION reject_prior_authority_data_mutation();

CREATE TRIGGER prior_authority_data_no_truncate
BEFORE TRUNCATE ON prior_authority_data
FOR EACH STATEMENT EXECUTE FUNCTION reject_prior_authority_data_mutation();

CREATE TABLE prior_authority_current_state (
    submission_id  UUID        NOT NULL,
    application_id UUID        NOT NULL,
    status         VARCHAR(20) NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (submission_id)
);
