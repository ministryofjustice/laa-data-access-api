CREATE TABLE application_data (
    application_id UUID NOT NULL,
    version BIGINT NOT NULL,
    payload JSONB NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (application_id, version)
);

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
