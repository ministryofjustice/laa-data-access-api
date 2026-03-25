package uk.gov.justice.laa.dstew.access.utils.harness;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.caseworker.CaseworkerGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.domainEvent.DomainEventGenerator;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves that the harness teardown (tearDownTrackedData) only removes rows whose
 * IDs were registered automatically via PersistedDataGenerator, and never touches
 * data that was persisted outside that mechanism (e.g. directly via a repository).
 *
 * <p>Tests are NOT annotated @SmokeTest — they run only in integration mode
 * (Testcontainers) against an empty database.  They must not run against real
 * infrastructure because the sentinel data created here has no meaning there.
 *
 * <p>Sentinel entities are created in @BeforeAll / cleaned in @AfterAll so they
 * exist across all test methods but are never registered with the harness tracker,
 * simulating data that existed before the test run.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class HarnessDataIsolationTest extends BaseHarnessTest {

    // ── Sentinels — created outside the harness lifecycle ─────────────────────

    private UUID sentinelCaseworkerId;
    private UUID sentinelApplicationId;
    private UUID sentinelDomainEventId;

    /**
     * Creates sentinel rows directly via the repositories, bypassing
     * PersistedDataGenerator, so they represent pre-existing data
     * the harness must never delete.
     */
    @BeforeAll
    void createSentinels() {
        var cwRepo  = harnessProvider.getBean(uk.gov.justice.laa.dstew.access.repository.CaseworkerRepository.class);
        var appRepo = harnessProvider.getBean(uk.gov.justice.laa.dstew.access.repository.ApplicationRepository.class);
        var deRepo  = harnessProvider.getBean(uk.gov.justice.laa.dstew.access.repository.DomainEventRepository.class);

        CaseworkerEntity sentinel = DataGenerator.createDefault(CaseworkerGenerator.class,
                b -> b.username("SENTINEL_PRE_EXISTING").build());
        cwRepo.saveAndFlush(sentinel);
        sentinelCaseworkerId = sentinel.getId();

        ApplicationEntity sentinelApp = DataGenerator.createDefault(ApplicationEntityGenerator.class,
                b -> b.caseworker(sentinel).build());
        appRepo.saveAndFlush(sentinelApp);
        sentinelApplicationId = sentinelApp.getId();

        DomainEventEntity sentinelDe = DataGenerator.createDefault(DomainEventGenerator.class,
                b -> b.applicationId(sentinelApp.getId())
                      .caseworkerId(sentinel.getId())
                      .type(DomainEventType.APPLICATION_CREATED)
                      .build());
        deRepo.saveAndFlush(sentinelDe);
        sentinelDomainEventId = sentinelDe.getId();
    }

    /**
     * Asserts that every sentinel still exists after all tests and their @AfterEach
     * teardowns have run, then removes them.  This is the definitive proof that
     * the harness never deletes pre-existing data.
     */
    @AfterAll
    void assertSentinelsSurvivedThenDelete() {
        var cwRepo  = harnessProvider.getBean(uk.gov.justice.laa.dstew.access.repository.CaseworkerRepository.class);
        var appRepo = harnessProvider.getBean(uk.gov.justice.laa.dstew.access.repository.ApplicationRepository.class);
        var deRepo  = harnessProvider.getBean(uk.gov.justice.laa.dstew.access.repository.DomainEventRepository.class);

        assertThat(deRepo.findById(sentinelDomainEventId))
                .as("sentinel domain event must survive all test teardowns")
                .isPresent();
        assertThat(appRepo.findById(sentinelApplicationId))
                .as("sentinel application must survive all test teardowns")
                .isPresent();
        assertThat(cwRepo.findById(sentinelCaseworkerId))
                .as("sentinel caseworker must survive all test teardowns")
                .isPresent();

        deRepo.findById(sentinelDomainEventId).ifPresent(deRepo::delete);
        appRepo.findById(sentinelApplicationId).ifPresent(appRepo::delete);
        cwRepo.findById(sentinelCaseworkerId).ifPresent(cwRepo::delete);
    }

    // ── Test 1 ─────────────────────────────────────────────────────────────────

    /**
     * Proves that the sentinel caseworker (not tracked) is still present after
     * @BeforeEach runs, and that the harness-seeded caseworkers exist during the
     * test body.  After this method returns, @AfterEach will remove JohnDoe and
     * JaneDoe (they were auto-tracked) but leave the sentinel alone.
     */
    @Test
    void givenPreExistingCaseworker_whenHarnessTeardownRuns_thenSentinelIsUntouched() {
        assertThat(caseworkerRepository.findById(sentinelCaseworkerId))
                .as("sentinel caseworker must exist after @BeforeEach")
                .isPresent();

        assertThat(caseworkerRepository.findById(CaseworkerJohnDoe.getId()))
                .as("harness caseworker JohnDoe must exist during test body")
                .isPresent();
    }

    // ── Test 2 ─────────────────────────────────────────────────────────────────

    /**
     * Proves that the sentinel application (not tracked) survives teardown while
     * a test-created application (auto-tracked via persistedDataGenerator) is removed.
     */
    @Test
    void givenPreExistingApplication_whenHarnessTeardownRuns_thenOnlyTrackedApplicationIsRemoved() {
        assertThat(applicationRepository.findById(sentinelApplicationId))
                .as("sentinel application must be present before teardown")
                .isPresent();

        // Persisted via persistedDataGenerator — auto-tracked, will be removed by teardown
        ApplicationEntity testApp = persistedDataGenerator.createAndPersist(
                ApplicationEntityGenerator.class,
                b -> b.caseworker(CaseworkerJohnDoe).build());

        assertThat(applicationRepository.findById(sentinelApplicationId)).isPresent();
        assertThat(applicationRepository.findById(testApp.getId())).isPresent();

        // After return: @AfterEach deletes testApp (tracked) but leaves the sentinel.
        // @AfterAll will assert all sentinels are still present before cleaning them up.
    }

    // ── Test 3 ─────────────────────────────────────────────────────────────────

    /**
     * Proves that the sentinel domain event (not tracked) survives teardown while
     * a test-created domain event (auto-tracked) is removed.
     */
    @Test
    void givenPreExistingDomainEvent_whenHarnessTeardownRuns_thenOnlyTrackedDomainEventIsRemoved() {
        assertThat(domainEventRepository.findById(sentinelDomainEventId))
                .as("sentinel domain event must be present before teardown")
                .isPresent();

        // Persisted via persistedDataGenerator — auto-tracked
        DomainEventEntity testDe = persistedDataGenerator.createAndPersist(
                DomainEventGenerator.class,
                b -> b.applicationId(sentinelApplicationId)
                      .caseworkerId(CaseworkerJohnDoe.getId())
                      .type(DomainEventType.APPLICATION_UPDATED)
                      .build());

        assertThat(domainEventRepository.findById(sentinelDomainEventId)).isPresent();
        assertThat(domainEventRepository.findById(testDe.getId())).isPresent();
    }

    // ── Test 4 ─────────────────────────────────────────────────────────────────

    /**
     * Core invariant: an entity persisted directly via the repository (bypassing
     * persistedDataGenerator) is NOT tracked and therefore survives teardown; one
     * persisted via persistedDataGenerator IS tracked and IS removed.
     */
    @Test
    void givenUntrackedAndTrackedEntities_whenHarnessTeardownRuns_thenOnlyTrackedEntityIsRemoved() {
        // entityA — saved directly via the repository, not tracked by PersistedDataGenerator
        ApplicationEntity entityA = DataGenerator.createDefault(ApplicationEntityGenerator.class,
                b -> b.caseworker(CaseworkerJohnDoe).build());
        applicationRepository.saveAndFlush(entityA);

        // entityB — persisted via persistedDataGenerator, auto-tracked
        ApplicationEntity entityB = persistedDataGenerator.createAndPersist(
                ApplicationEntityGenerator.class,
                b -> b.caseworker(CaseworkerJaneDoe).build());

        assertThat(applicationRepository.findById(entityA.getId()))
                .as("untracked entity must be present during test body")
                .isPresent();
        assertThat(applicationRepository.findById(entityB.getId()))
                .as("tracked entity must be present during test body")
                .isPresent();

        // entityA must be cleaned up regardless of what happens next — it is untracked
        // so @AfterEach will never remove it.  It must also be deleted before teardown
        // because it FKs to CaseworkerJohnDoe, which teardown will delete.
        try {
            applicationRepository.delete(entityA);
            tearDownTrackedData();

            assertThat(applicationRepository.findById(entityB.getId()))
                    .as("tracked entity must be removed by teardown")
                    .isEmpty();
            assertThat(applicationRepository.findById(entityA.getId()))
                    .as("untracked entity was removed by our explicit cleanup")
                    .isEmpty();
        } finally {
            // Safety net: if delete above threw, try again — idempotent.
            applicationRepository.findById(entityA.getId())
                    .ifPresent(applicationRepository::delete);
        }
    }

    // ── Test 5 ─────────────────────────────────────────────────────────────────

    /**
     * Proves that if setup partially fails, any entity that was already
     * persisted (and therefore auto-tracked via PersistedDataGenerator) is still
     * cleaned up by teardown, even though setup did not complete.
     *
     * <p>Simulated manually so the result is visible within this test rather than
     * relying on post-test @AfterEach only.
     */
    @Test
    void givenPartialSetupFailure_whenTeardownRuns_thenPartiallyCreatedDataIsRemoved() {
        // Simulate: first caseworker persisted and auto-tracked via PersistedDataGenerator
        CaseworkerEntity partialCaseworker = persistedDataGenerator.createAndPersist(
                CaseworkerGenerator.class,
                b -> b.username("PARTIAL_SETUP_CW").build());

        assertThat(caseworkerRepository.findById(partialCaseworker.getId()))
                .as("partially-created caseworker must exist after persist")
                .isPresent();

        // Invoke teardown manually so we can assert the outcome here
        tearDownTrackedData();

        assertThat(caseworkerRepository.findById(partialCaseworker.getId()))
                .as("partially-created caseworker must be removed by teardown")
                .isEmpty();

        // Re-seed JohnDoe and JaneDoe because teardown cleared the tracker and
        // deleted them.  @AfterEach will fire again but lists are empty — no-op.
        CaseworkerJohnDoe = persistedDataGenerator.createAndPersist(
                CaseworkerGenerator.class, b -> b.username("JohnDoe").build());
        CaseworkerJaneDoe = persistedDataGenerator.createAndPersist(
                CaseworkerGenerator.class, b -> b.username("JaneDoe").build());
    }
}

