-- Add merits_decision_id to proceedings so ProceedingEntityV2 can own the FK.
-- linked_merits_decisions and merits_decisions.proceeding_id are intentionally
-- left in place so the original entities (used by ApplicationService) continue to work.
-- proceeding_id is made nullable so MeritsDecisionEntityV2 (which has no proceeding_id field)
-- can be inserted without violating the NOT NULL constraint.

ALTER TABLE merits_decisions
    ALTER COLUMN proceeding_id DROP NOT NULL;

ALTER TABLE proceedings
    ADD COLUMN merits_decision_id UUID NULL;

-- Wire up any existing rows (no-op in a fresh environment)
UPDATE proceedings p
SET merits_decision_id = md.id
FROM merits_decisions md
WHERE md.proceeding_id = p.id;

ALTER TABLE proceedings
    ADD CONSTRAINT fk_proceedings_merits_decision_id
        FOREIGN KEY (merits_decision_id) REFERENCES merits_decisions(id) ON DELETE SET NULL;


