-- Convert MeritsDecisions from ManyToMany to OneToMany with Decision
-- Add decisions_id FK column to merits_decisions table
ALTER TABLE merits_decisions
    ADD COLUMN decisions_id UUID;

-- Migrate data from join table
UPDATE merits_decisions md
SET decisions_id = lmd.decisions_id
FROM linked_merits_decisions lmd
WHERE md.id = lmd.merits_decisions_id;

-- Make FK NOT NULL
ALTER TABLE merits_decisions
    ALTER COLUMN decisions_id SET NOT NULL;

-- Add FK constraint
ALTER TABLE merits_decisions
    ADD CONSTRAINT fk_merits_decisions_decisions_id
        FOREIGN KEY (decisions_id) REFERENCES decisions(id) ON DELETE CASCADE;

-- Drop the join table (no longer needed)
DROP TABLE linked_merits_decisions;
