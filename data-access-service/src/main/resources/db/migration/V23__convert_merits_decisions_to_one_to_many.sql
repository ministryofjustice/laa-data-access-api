-- Migrate MeritsDecision from ManyToMany (via linked_merits_decisions join table)
-- to a direct OneToMany FK (decisions_id on merits_decisions).

ALTER TABLE merits_decisions ADD COLUMN decisions_id UUID;

UPDATE merits_decisions md
  SET decisions_id = lmd.decisions_id
  FROM linked_merits_decisions lmd
  WHERE md.id = lmd.merits_decisions_id;

ALTER TABLE merits_decisions ALTER COLUMN decisions_id SET NOT NULL;

ALTER TABLE merits_decisions
  ADD CONSTRAINT fk_merits_decisions_decisions_id
    FOREIGN KEY (decisions_id) REFERENCES decisions(id) ON DELETE CASCADE;

DROP TABLE linked_merits_decisions;
