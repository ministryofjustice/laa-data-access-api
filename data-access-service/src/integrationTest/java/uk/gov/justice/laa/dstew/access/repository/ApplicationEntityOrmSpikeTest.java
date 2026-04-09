package uk.gov.justice.laa.dstew.access.repository;

import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.DecisionEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.DecisionStatus;
import uk.gov.justice.laa.dstew.access.utils.BaseIntegrationTest;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.decision.DecisionEntityGenerator;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ApplicationEntityOrmSpikeTest extends BaseIntegrationTest {

    // ─── Group A: Partial updates with LAZY @OneToOne relationships ───────────

    // AC1: Managed entity — updating a scalar field within an active transaction
    // must not nullify decision_id or caseworker_id in the UPDATE statement.
    @Test
    void managedEntityPartialUpdate_scalarFieldOnly_fkColumnsPreserved() {
        // Arrange
        var application = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class,
                builder -> builder.caseworker(CaseworkerJohnDoe).linkedApplications(Set.of()));
        var decision = persistedDataGenerator.createAndPersist(DecisionEntityGenerator.class, b -> {});
        application.setDecision(decision);
        applicationRepository.saveAndFlush(application);
        clearCache();

        // Act: load the entity and update only a scalar field — do not touch decision or caseworker
        var loaded = applicationRepository.findById(application.getId()).orElseThrow();
        loaded.setStatus(ApplicationStatus.APPLICATION_IN_PROGRESS);
        applicationRepository.saveAndFlush(loaded);
        clearCache();

        // Assert: FK columns must not be changed by the scalar-only UPDATE
        var reloaded = applicationRepository.findById(application.getId()).orElseThrow();
        assertThat(reloaded.getDecision())
                .as("decision must not be nullified by a scalar-only partial update (managed entity)")
                .isNotNull();
        assertThat(reloaded.getCaseworker())
                .as("caseworker must not be nullified by a scalar-only partial update (managed entity)")
                .isNotNull();
    }

    // AC2: Detached entity — re-attaching via save() after detach must not
    // nullify FK columns. Documents the actual Hibernate merge behaviour.
    @Test
    void detachedEntityReattach_scalarUpdate_fkColumnsDocumented() {
        // Arrange
        var application = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class,
                builder -> builder.caseworker(CaseworkerJohnDoe).linkedApplications(Set.of()));
        var decision = persistedDataGenerator.createAndPersist(DecisionEntityGenerator.class, b -> {});
        application.setDecision(decision);
        applicationRepository.saveAndFlush(application);

        // Detach the entity — lazy proxies for decision and caseworker are now uninitialised
        entityManager.detach(application);

        // Act: modify a scalar field on the detached entity and re-attach via save() (merge)
        application.setStatus(ApplicationStatus.APPLICATION_IN_PROGRESS);
        applicationRepository.save(application);
        clearCache();

        // Assert: document whether Hibernate merge preserves or nullifies uninitialised LAZY FK proxies
        var reloaded = applicationRepository.findById(application.getId()).orElseThrow();
        assertThat(reloaded.getDecision())
                .as("decision FK must not be nullified when merging a detached entity with uninitialised LAZY proxy")
                .isNotNull();
        assertThat(reloaded.getCaseworker())
                .as("caseworker FK must not be nullified when merging a detached entity with uninitialised LAZY proxy")
                .isNotNull();
    }

    // AC3: Manually constructed entity with an existing ID — saving a new builder
    // instance with only required fields set risks silently overwriting FK columns.
    @Test
    void newEntityWithExistingId_noRelationshipsSet_fkColumnBehaviourDocumented() {
        // Arrange: persist a fully-linked entity to establish FK rows in DB
        var persisted = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class,
                builder -> builder.caseworker(CaseworkerJohnDoe).linkedApplications(Set.of()));
        var decision = persistedDataGenerator.createAndPersist(DecisionEntityGenerator.class, b -> {});
        persisted.setDecision(decision);
        applicationRepository.saveAndFlush(persisted);
        clearCache();

        // Act: construct a NEW entity instance using only the existing ID (no relationships set)
        // Required NOT NULL fields are carried over to avoid DB constraint violations unrelated to the FK test
        var partial = ApplicationEntity.builder()
                .id(persisted.getId())
                .version(persisted.getVersion())
                .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
                .applyApplicationId(persisted.getApplyApplicationId())
                .submittedAt(persisted.getSubmittedAt())
                .applicationContent(persisted.getApplicationContent())
                .build(); // decision and caseworker intentionally null
        applicationRepository.save(partial);
        clearCache();

        // Assert: RISK CONFIRMED — Hibernate merge overwrites all columns with the partial entity's values.
        // Both FK columns are silently nullified in the UPDATE statement even though they were populated in the DB.
        // Mitigation: never construct a partial entity with an existing ID; always load-and-modify within a transaction.
        var reloaded = applicationRepository.findById(persisted.getId()).orElseThrow();
        assertThat(reloaded.getDecision())
                .as("FINDING: decision FK is silently nullified by Hibernate merge when the partial entity has decision=null")
                .isNull();
        assertThat(reloaded.getCaseworker())
                .as("FINDING: caseworker FK is silently nullified by Hibernate merge when the partial entity has caseworker=null")
                .isNull();
    }

    // ─── Group B: Implicit insert behaviour on save ───────────────────────────

    // AC4: Persisting a new ApplicationEntity with decision=null (default from generator)
    // must not emit an INSERT INTO decisions.
    @Test
    void saveApplicationWithNullDecision_noImplicitInsertIntoDecisions() {
        long decisionCountBefore = decisionRepository.count();

        // Act: persist a new application — ApplicationEntityGenerator does not set decision
        persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class,
                builder -> builder.caseworker(CaseworkerJohnDoe).linkedApplications(Set.of()));
        clearCache();

        // Assert: no row must have been inserted into decisions
        assertThat(decisionRepository.count())
                .as("saving ApplicationEntity with decision=null must not INSERT a row into decisions")
                .isEqualTo(decisionCountBefore);
    }

    // AC5: Setting decision to a new transient DecisionEntity and saving documents
    // whether the absence of cascade on ApplicationEntity.decision triggers an exception
    // (expected: TransientPropertyValueException) or a silent implicit INSERT.
    // FINDING: Hibernate throws TransientPropertyValueException — no implicit insert occurs.
    @Test
    void saveApplicationWithNewTransientDecision_throwsTransientPropertyValueException() {
        // Arrange: persist application without a decision
        var application = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class,
                builder -> builder.caseworker(CaseworkerJohnDoe).linkedApplications(Set.of()));
        clearCache();

        // Act: reload and assign a new transient (unsaved) DecisionEntity
        var loaded = applicationRepository.findById(application.getId()).orElseThrow();
        var transientDecision = DecisionEntity.builder()
                .overallDecision(DecisionStatus.REFUSED)
                .build(); // not yet persisted — no cascade defined on ApplicationEntity.decision
        loaded.setDecision(transientDecision);

        // Assert: Hibernate must throw rather than silently insert — this is the safe behaviour.
        // No implicit INSERT into decisions occurs because no cascade is configured.
        // Spring wraps the exception: InvalidDataAccessApiUsageException -> IllegalStateException -> TransientPropertyValueException
        assertThatThrownBy(() -> applicationRepository.saveAndFlush(loaded))
                .as("setting decision to a new transient DecisionEntity must throw TransientPropertyValueException — not silently insert")
                .hasRootCauseInstanceOf(org.hibernate.TransientPropertyValueException.class);
    }

    // AC6: Verifies that Lombok @Builder does not default decision or caseworker
    // to non-null instances, which would cause implicit inserts.
    @Test
    void applicationEntityBuilder_defaultConstruction_decisionAndCaseworkerAreNull() {
        var application = ApplicationEntity.builder()
                .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
                .build();

        assertThat(application.getDecision())
                .as("Lombok @Builder must not initialise decision to a new DecisionEntity instance")
                .isNull();
        assertThat(application.getCaseworker())
                .as("Lombok @Builder must not initialise caseworker to a new CaseworkerEntity instance")
                .isNull();
    }
}
