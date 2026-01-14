-- ============================================================
-- Enforce NOT NULL constraints
-- ============================================================

ALTER TABLE applications
    ALTER COLUMN apply_application_id SET NOT NULL,
    ALTER COLUMN submitted_at SET NOT NULL,
    ALTER COLUMN used_delegated_functions SET NOT NULL,
    ALTER COLUMN is_auto_granted SET NOT NULL;