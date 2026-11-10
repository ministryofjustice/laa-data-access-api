CREATE TABLE linked_group_justification (
    id UUID PRIMARY KEY,
    group_id UUID NOT NULL,
    justification TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE FUNCTION reject_linked_group_justification_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'linked_group_justification is append-only; % is prohibited', TG_OP;
END;
$$;

CREATE TRIGGER linked_group_justification_no_update
BEFORE UPDATE ON linked_group_justification
FOR EACH ROW EXECUTE FUNCTION reject_linked_group_justification_mutation();

CREATE TRIGGER linked_group_justification_no_direct_delete
BEFORE DELETE ON linked_group_justification
FOR EACH ROW
WHEN (current_setting('application.retention_delete', true) IS DISTINCT FROM 'enabled')
EXECUTE FUNCTION reject_linked_group_justification_mutation();

CREATE TRIGGER linked_group_justification_no_truncate
BEFORE TRUNCATE ON linked_group_justification
FOR EACH STATEMENT EXECUTE FUNCTION reject_linked_group_justification_mutation();

ALTER TABLE application_history ADD COLUMN justification_id UUID;
