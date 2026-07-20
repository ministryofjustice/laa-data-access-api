ALTER TABLE application_data
    DROP COLUMN IF EXISTS pii_ref,
    DROP COLUMN IF EXISTS pii_valid_from,
    DROP COLUMN IF EXISTS pii_valid_until;

DROP INDEX IF EXISTS idx_pii_ref;
DROP INDEX IF EXISTS idx_application_data_pii_temporal;
