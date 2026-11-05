-- Original filenames of a draft's documents, projected from ApplicationDocumentUploadedEvent
-- content so document downloads keep reading from the projection now that application_draft
-- is gone. Cleared when the draft is submitted; replay after erasure leaves them absent.
ALTER TABLE application_current_state
    ADD COLUMN draft_document_filenames JSONB;
