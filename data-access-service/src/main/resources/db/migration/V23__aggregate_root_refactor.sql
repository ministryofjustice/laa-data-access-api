-- Add merits_decision_id to proceedings so ProceedingEntityV2 can own the FK.
-- linked_merits_decisions and merits_decisions.proceeding_id are intentionally
-- left in place so the original entities (used by ApplicationService) continue to work.
-- proceeding_id is made nullable so MeritsDecisionEntityV2 (which has no proceeding_id field)
-- can be inserted without violating the NOT NULL constraint.

ALTER TABLE merits_decisions
    ALTER COLUMN proceeding_id DROP NOT NULL;

ALTER TABLE proceedings
    ADD COLUMN merits_decision_id UUID NULL;

ALTER TABLE proceedings
    ADD CONSTRAINT fk_proceedings_merits_decision_id
        FOREIGN KEY (merits_decision_id) REFERENCES merits_decisions(id) ON DELETE SET NULL;

-- applications
-- apply_application_id: hot path — existsByApplyApplicationId + findByApplyApplicationId +
--   findAllByApplyApplicationIdIn are called on every create and linked-application lookup.
--   UNIQUE because CreateApplicationUseCase rejects duplicates.
CREATE UNIQUE INDEX idx_applications_apply_application_id
    ON applications (apply_application_id);

-- caseworker_id: FK backing index (ON DELETE SET NULL cascade + @OneToOne join)
CREATE INDEX idx_applications_caseworker_id
    ON applications (caseworker_id);

-- proceedings
-- application_id: hot path — @Fetch(FetchMode.SUBSELECT) fires
--   "SELECT … WHERE application_id IN (…)" on every aggregate load.
CREATE INDEX idx_proceedings_application_id
    ON proceedings (application_id);

-- merits_decision_id: FK added in this migration; backing index for the @OneToOne JOIN and
--   ON DELETE SET NULL cascade.
CREATE INDEX idx_proceedings_merits_decision_id
    ON proceedings (merits_decision_id);

-- merits_decisions
-- proceeding_id: FK backing index (ON DELETE CASCADE from proceedings)
CREATE INDEX idx_merits_decisions_proceeding_id
    ON merits_decisions (proceeding_id);

-- domain_events
-- application_id: FK backing index; queried via JpaSpecificationExecutor filters
CREATE INDEX idx_domain_events_application_id
    ON domain_events (application_id);

-- caseworker_id: FK backing index (ON DELETE SET NULL cascade)
CREATE INDEX idx_domain_events_caseworker_id
    ON domain_events (caseworker_id);

-- linked_applications
-- Both FK columns need indexes — lead_application_id is queried by the @OneToMany on
-- ApplicationEntityV2; associated_application_id is used for ON DELETE CASCADE.
CREATE INDEX idx_linked_applications_lead_application_id
    ON linked_applications (lead_application_id);

CREATE INDEX idx_linked_applications_associated_application_id
    ON linked_applications (associated_application_id);

-- application_notes
-- application_id: queried by findByApplicationId and findByApplicationIdOrderByCreatedAtAsc
CREATE INDEX idx_application_notes_application_id
    ON application_notes (application_id);

-- linked_individuals
-- individual_id: the composite PK covers lookups by (application_id, individual_id) but not
--   lookups by individual_id alone, which PostgreSQL needs for ON DELETE CASCADE from individuals.
CREATE INDEX idx_linked_individuals_individual_id
    ON linked_individuals (individual_id);


