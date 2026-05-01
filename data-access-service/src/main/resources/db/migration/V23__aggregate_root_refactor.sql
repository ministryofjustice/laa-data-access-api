-- 1. Add merits_decision_id to proceedings (nullable to start — existing rows have no value yet)
ALTER TABLE proceedings
    ADD COLUMN IF NOT EXISTS merits_decision_id UUID NULL;

-- 2. Backfill: for each row in linked_merits_decisions, copy the FK across
--    (each proceeding should have at most one merits decision; if multiples exist, pick the latest)
UPDATE proceedings p
SET    merits_decision_id = lmd.merits_decisions_id
FROM   linked_merits_decisions lmd
JOIN   merits_decisions md ON md.id = lmd.merits_decisions_id
WHERE  md.proceeding_id = p.id;

-- 3. Add FK constraint (nullable — not all proceedings will have a merits decision yet)
--    Use DO block to make this idempotent (PostgreSQL does not support IF NOT EXISTS for constraints)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_proceedings_merits_decision_id'
          AND table_name = 'proceedings'
    ) THEN
        ALTER TABLE proceedings
            ADD CONSTRAINT fk_proceedings_merits_decision_id
                FOREIGN KEY (merits_decision_id)
                REFERENCES merits_decisions(id)
                ON DELETE SET NULL;
    END IF;
END $$;

-- 4. Remove the old proceeding_id FK from merits_decisions
--    (the relationship is now owned by proceedings.merits_decision_id)
ALTER TABLE merits_decisions
    DROP CONSTRAINT IF EXISTS fk_merits_decisions_proceedings_id;

ALTER TABLE merits_decisions
    DROP COLUMN IF EXISTS proceeding_id;

-- 5. Drop the linked_merits_decisions join table (no longer needed)
DROP TABLE IF EXISTS linked_merits_decisions;

