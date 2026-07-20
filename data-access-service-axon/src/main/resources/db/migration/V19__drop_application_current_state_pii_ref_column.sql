ALTER TABLE application_current_state DROP COLUMN IF EXISTS current_pii_ref;

DROP INDEX IF EXISTS idx_application_current_pii_ref;
