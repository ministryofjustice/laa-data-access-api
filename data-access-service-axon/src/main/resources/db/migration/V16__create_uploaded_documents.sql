CREATE TABLE uploaded_documents (
    document_id       UUID        NOT NULL,
    submission_id     UUID        NOT NULL,
    original_filename TEXT        NOT NULL,
    PRIMARY KEY (document_id)
);

CREATE INDEX idx_uploaded_documents_submission_id
    ON uploaded_documents (submission_id);

ALTER TABLE prior_authority_current_state
    ADD COLUMN uploaded_document_ids JSONB NOT NULL DEFAULT '[]'::jsonb;

INSERT INTO uploaded_documents (
    document_id, submission_id, original_filename)
SELECT
    (document->>'documentId')::uuid AS document_id,
    source.prior_authority_id,
    document->>'fileName'
FROM (
    SELECT prior_authority_id, payload
    FROM prior_authority_draft
    UNION ALL
    SELECT prior_authority_id, payload
    FROM prior_authority_data
) source
CROSS JOIN LATERAL jsonb_array_elements(COALESCE(source.payload->'content'->'uploadedDocuments', '[]'::jsonb)) document
ON CONFLICT (document_id) DO NOTHING;

UPDATE prior_authority_current_state current_state
SET uploaded_document_ids = documents.document_ids
FROM (
    SELECT
        prior_authority_id,
        jsonb_agg(
            jsonb_build_object(
                'documentId', document->>'documentId',
                'size', (document->>'size')::bigint,
                'fileType', document->>'fileType',
                'contentType', document->>'mediaType',
                'sourceService', document->>'sourceService',
                'documentType', document->>'documentType',
                'uploadedAt', (document->>'uploadedAt')::timestamptz,
                'deletedAt', NULL
            )
            ORDER BY (document->>'uploadedAt')::timestamptz
        ) AS document_ids
    FROM (
        SELECT
            source.prior_authority_id,
            document
        FROM (
            SELECT prior_authority_id, payload
            FROM prior_authority_draft
            UNION ALL
            SELECT prior_authority_id, payload
            FROM prior_authority_data
        ) source
        CROSS JOIN LATERAL jsonb_array_elements(COALESCE(source.payload->'content'->'uploadedDocuments', '[]'::jsonb)) document
    ) uploaded
    GROUP BY prior_authority_id
) documents
WHERE current_state.prior_authority_id = documents.prior_authority_id;