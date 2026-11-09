CREATE TABLE document_filename (
    owner_id    UUID        NOT NULL,
    document_id UUID        NOT NULL,
    filename    TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    deleted_at  TIMESTAMPTZ,
    PRIMARY KEY (document_id)
);

CREATE INDEX idx_document_filename_owner_id ON document_filename (owner_id);

DO $$
DECLARE
    conflicting_count BIGINT;
BEGIN
    WITH legacy AS (
        SELECT d.application_id AS owner_id, e.document_id_text::uuid AS document_id
        FROM application_draft d
        CROSS JOIN LATERAL jsonb_each_text(
            CASE WHEN jsonb_typeof(d.payload -> 'documentFilenames') = 'object'
                 THEN d.payload -> 'documentFilenames' END) AS e(document_id_text, filename)
        WHERE btrim(e.filename) <> ''
        UNION
        SELECT d.prior_authority_id, e.document_id_text::uuid
        FROM prior_authority_draft d
        CROSS JOIN LATERAL jsonb_each_text(
            CASE WHEN jsonb_typeof(d.payload -> 'documentFilenames') = 'object'
                 THEN d.payload -> 'documentFilenames' END) AS e(document_id_text, filename)
        WHERE btrim(e.filename) <> ''
        UNION
        SELECT d.application_id, e.document_id_text::uuid
        FROM application_data d
        CROSS JOIN LATERAL jsonb_each_text(
            CASE WHEN jsonb_typeof(d.payload -> 'documentFilenames') = 'object'
                 THEN d.payload -> 'documentFilenames' END) AS e(document_id_text, filename)
        WHERE btrim(e.filename) <> ''
          AND d.version = (SELECT max(l.version) FROM application_data l
                           WHERE l.application_id = d.application_id)
        UNION
        SELECT d.prior_authority_id, e.document_id_text::uuid
        FROM prior_authority_data d
        CROSS JOIN LATERAL jsonb_each_text(
            CASE WHEN jsonb_typeof(d.payload -> 'documentFilenames') = 'object'
                 THEN d.payload -> 'documentFilenames' END) AS e(document_id_text, filename)
        WHERE btrim(e.filename) <> ''
          AND d.data_version = (SELECT max(l.data_version) FROM prior_authority_data l
                                WHERE l.prior_authority_id = d.prior_authority_id)
    )
    SELECT count(*) INTO conflicting_count
    FROM (SELECT document_id FROM legacy GROUP BY document_id HAVING count(*) > 1) conflicts;

    IF conflicting_count > 0 THEN
        RAISE EXCEPTION 'document_filename backfill aborted: % document_id value(s) appear under more than one owner',
            conflicting_count;
    END IF;
END $$;

INSERT INTO document_filename (owner_id, document_id, filename, created_at)
SELECT d.application_id, e.document_id_text::uuid, e.filename, d.updated_at
FROM application_draft d
CROSS JOIN LATERAL jsonb_each_text(
    CASE WHEN jsonb_typeof(d.payload -> 'documentFilenames') = 'object'
         THEN d.payload -> 'documentFilenames' END) AS e(document_id_text, filename)
WHERE btrim(e.filename) <> ''
ON CONFLICT (document_id) DO NOTHING;

INSERT INTO document_filename (owner_id, document_id, filename, created_at)
SELECT d.prior_authority_id, e.document_id_text::uuid, e.filename, d.updated_at
FROM prior_authority_draft d
CROSS JOIN LATERAL jsonb_each_text(
    CASE WHEN jsonb_typeof(d.payload -> 'documentFilenames') = 'object'
         THEN d.payload -> 'documentFilenames' END) AS e(document_id_text, filename)
WHERE btrim(e.filename) <> ''
ON CONFLICT (document_id) DO NOTHING;

INSERT INTO document_filename (owner_id, document_id, filename, created_at)
SELECT d.application_id, e.document_id_text::uuid, e.filename, d.created_at
FROM application_data d
CROSS JOIN LATERAL jsonb_each_text(
    CASE WHEN jsonb_typeof(d.payload -> 'documentFilenames') = 'object'
         THEN d.payload -> 'documentFilenames' END) AS e(document_id_text, filename)
WHERE btrim(e.filename) <> ''
  AND d.version = (SELECT max(l.version) FROM application_data l
                   WHERE l.application_id = d.application_id)
ON CONFLICT (document_id) DO NOTHING;

INSERT INTO document_filename (owner_id, document_id, filename, created_at)
SELECT d.prior_authority_id, e.document_id_text::uuid, e.filename, d.created_at
FROM prior_authority_data d
CROSS JOIN LATERAL jsonb_each_text(
    CASE WHEN jsonb_typeof(d.payload -> 'documentFilenames') = 'object'
         THEN d.payload -> 'documentFilenames' END) AS e(document_id_text, filename)
WHERE btrim(e.filename) <> ''
  AND d.data_version = (SELECT max(l.data_version) FROM prior_authority_data l
                        WHERE l.prior_authority_id = d.prior_authority_id)
ON CONFLICT (document_id) DO NOTHING;

CREATE OR REPLACE FUNCTION delete_application_data_for_retention(target_application_id UUID)
RETURNS BIGINT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, axon
AS $$
DECLARE
    deleted_count BIGINT;
BEGIN
    DELETE FROM axon.document_filename
    WHERE owner_id = target_application_id
       OR owner_id IN (
            SELECT prior_authority_id FROM axon.prior_authority_draft WHERE application_id = target_application_id
            UNION
            SELECT prior_authority_id FROM axon.prior_authority_data WHERE application_id = target_application_id
       );

    PERFORM set_config('application.retention_delete', 'enabled', true);
    DELETE FROM axon.application_data WHERE application_id = target_application_id;
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    PERFORM set_config('application.retention_delete', 'disabled', true);
    RETURN deleted_count;
END;
$$;

REVOKE ALL ON FUNCTION delete_application_data_for_retention(UUID) FROM PUBLIC;
