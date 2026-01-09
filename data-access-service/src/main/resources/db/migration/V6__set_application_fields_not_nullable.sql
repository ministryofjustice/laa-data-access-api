-- ============================================================
-- Backfill existing NULL values to allow NOT NULL constraints
-- ============================================================

-- apply_application_id
UPDATE applications
SET apply_application_id = gen_random_uuid()
WHERE apply_application_id IS NULL;

-- submitted_at
UPDATE applications
SET submitted_at = created_at
WHERE submitted_at IS NULL;

-- used_delegated_functions
UPDATE applications
SET used_delegated_functions = false
WHERE used_delegated_functions IS NULL;

-- is_auto_granted
UPDATE applications
SET is_auto_granted = false
WHERE is_auto_granted IS NULL;

-- ============================================================
-- Enforce NOT NULL constraints
-- ============================================================

ALTER TABLE applications
    ALTER COLUMN apply_application_id SET NOT NULL,
    ALTER COLUMN submitted_at SET NOT NULL,
    ALTER COLUMN used_delegated_functions SET NOT NULL,
    ALTER COLUMN is_auto_granted SET NOT NULL;