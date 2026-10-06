ALTER TABLE prior_authority_current_state
    ADD COLUMN uploaded_documents JSONB NOT NULL DEFAULT '[]'::jsonb;
