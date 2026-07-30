CREATE TABLE caseworkers (
    id UUID PRIMARY KEY,
    username VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE application_data (
    application_id UUID NOT NULL,
    version BIGINT NOT NULL,
    payload JSONB NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (application_id, version)
);

CREATE TABLE application_current_state (
    application_id UUID PRIMARY KEY,
    status VARCHAR(255) NOT NULL,
    application_data_version BIGINT NOT NULL,
    application_version BIGINT NOT NULL,
    schema_version INTEGER NOT NULL,
    application_type VARCHAR(255) NOT NULL,
    apply_application_id UUID NOT NULL,
    lead_application_id UUID,
    caseworker_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    modified_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_application_current_state_caseworker
        FOREIGN KEY (caseworker_id) REFERENCES caseworkers(id)
);

CREATE TABLE linked_application_group_current_state (
    group_id UUID PRIMARY KEY,
    lead_application_id UUID NOT NULL,
    member_ids JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    modified_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE application_history (
    event_id VARCHAR(255) PRIMARY KEY,
    application_id UUID NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    request_payload JSONB NOT NULL,
    service_name VARCHAR(255),
    occurred_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX application_history_application_id_idx
    ON application_history (application_id);

CREATE FUNCTION reject_application_data_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'application_data is append-only; % is prohibited', TG_OP;
END;
$$;

CREATE TRIGGER application_data_no_update
BEFORE UPDATE ON application_data
FOR EACH ROW EXECUTE FUNCTION reject_application_data_mutation();

CREATE TRIGGER application_data_no_direct_delete
BEFORE DELETE ON application_data
FOR EACH ROW
WHEN (current_setting('application.retention_delete', true) IS DISTINCT FROM 'enabled')
EXECUTE FUNCTION reject_application_data_mutation();

CREATE TRIGGER application_data_no_truncate
BEFORE TRUNCATE ON application_data
FOR EACH STATEMENT EXECUTE FUNCTION reject_application_data_mutation();

CREATE FUNCTION delete_application_data_for_retention(target_application_id UUID)
RETURNS BIGINT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, axon
AS $$
DECLARE
    deleted_count BIGINT;
BEGIN
    PERFORM set_config('application.retention_delete', 'enabled', true);
    DELETE FROM axon.application_data WHERE application_id = target_application_id;
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    PERFORM set_config('application.retention_delete', 'disabled', true);
    RETURN deleted_count;
END;
$$;

REVOKE ALL ON FUNCTION delete_application_data_for_retention(UUID) FROM PUBLIC;
