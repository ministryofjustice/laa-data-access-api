package uk.gov.justice.laa.dstew.access.utils.generator;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.repository.*;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationSummaryGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.LinkedApplicationEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.caseworker.CaseworkerGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.certificate.CertificateEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.decision.DecisionEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.domainEvent.DomainEventGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.individual.IndividualEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.merit.MeritsDecisionsEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.proceeding.ProceedingsEntityGenerator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;


@Component
public class PersistedDataGenerator extends DataGenerator {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    // Map generator class to repository class
    private final Map<Class<?>, Class<? extends JpaRepository<?, ?>>> generatorRepoMap = new HashMap<>();

    // Tracking lists — populated automatically on every persist so that
    // deleteTrackedData() can clean up exactly what this generator created.
    private final List<UUID> trackedCaseworkerIds  = new ArrayList<>();
    private final List<UUID> trackedApplicationIds = new ArrayList<>();
    private final List<UUID> trackedDomainEventIds = new ArrayList<>();
    private final List<UUID> trackedIndividualIds  = new ArrayList<>();
    private final List<UUID> trackedDecisionIds    = new ArrayList<>();

    @PostConstruct
    public void init() {
        registerRepository(DomainEventGenerator.class, DomainEventRepository.class);
        registerRepository(ApplicationEntityGenerator.class, ApplicationRepository.class);
        registerRepository(ApplicationSummaryGenerator.class, ApplicationSummaryRepository.class);
        registerRepository(CaseworkerGenerator.class, CaseworkerRepository.class);
        registerRepository(DecisionEntityGenerator.class, DecisionRepository.class);
        registerRepository(ProceedingsEntityGenerator.class, ProceedingRepository.class);
        registerRepository(MeritsDecisionsEntityGenerator.class, MeritsDecisionRepository.class);
        registerRepository(CertificateEntityGenerator.class, CertificateRepository.class);
        registerRepository(IndividualEntityGenerator.class, IndividualRepository.class);
    }

    public <TGenerator, TRepository extends JpaRepository<?, ?>>
    void registerRepository(Class<TGenerator> generatorType, Class<TRepository> repositoryType) {
        generatorRepoMap.put(generatorType, repositoryType);
    }

    public <TEntity, TBuilder, TGenerator extends BaseGenerator<TEntity, TBuilder>>
    TEntity createAndPersist(Class<TGenerator> generatorType) {
        TEntity entity = DataGenerator.createDefault(generatorType);
        return persist(generatorType, entity);
    }

    public <TEntity, TBuilder, TGenerator extends BaseGenerator<TEntity, TBuilder>>
    TEntity createAndPersist(Class<TGenerator> generatorType, Consumer<TBuilder> customiser) {
        TEntity entity = DataGenerator.createDefault(generatorType, customiser);
        return persist(generatorType, entity);
    }

    public <TEntity, TBuilder, TGenerator extends BaseGenerator<TEntity, TBuilder>>
    List<TEntity> createAndPersistMultiple(Class<TGenerator> generatorType, int count) {
        List<TEntity> entities = DataGenerator.createMultipleDefault(generatorType, count);
        if (entities.isEmpty()) {
            return entities;
        }
        return persist(generatorType, entities);
    }

    public <TEntity, TBuilder, TGenerator extends BaseGenerator<TEntity, TBuilder>>
    List<TEntity> createAndPersistMultiple(Class<TGenerator> generatorType, int count, Consumer<TBuilder> customiser) {
        List<TEntity> entities = DataGenerator.createMultipleDefault(generatorType, count, customiser);
        if (entities.isEmpty()) {
            return entities;
        }
        return persist(generatorType, entities);
    }

    private <TEntity, TGenerator> JpaRepository<TEntity, ?> getRepository(Class<TGenerator> generatorType) {
        Class<? extends JpaRepository<?, ?>> repoClass = generatorRepoMap.get(generatorType);
        if (repoClass == null) {
            throw new IllegalArgumentException("No repository registered for generator: " + generatorType.getName());
        }
        return (JpaRepository<TEntity, ?>) applicationContext.getBean(repoClass);
    }

    /** Returns the IDs of applications persisted in the current test (useful for "nothing persisted" assertions). */
    public List<UUID> trackedApplicationIds() { return trackedApplicationIds; }

    /**
     * Registers an application ID that was created outside this generator (e.g. by the API
     * under test) so that {@link #deleteTrackedData()} will clean it up after the test.
     *
     * <p>Also looks up and registers every individual linked to the application so that orphaned
     * {@code individuals} rows are removed.  (Deleting an application CASCADE-deletes
     * {@code linked_individuals} join rows but not the {@code individuals} records themselves.)
     *
     * <p>Individual IDs are fetched via a direct JDBC query on {@code linked_individuals} to
     * avoid triggering a lazy-collection load outside of a JPA session.
     */
    public void trackExistingApplication(UUID applicationId) {
        if (!trackedApplicationIds.contains(applicationId)) {
            trackedApplicationIds.add(applicationId);
        }
        // Fetch linked individual IDs directly via JDBC — avoids LazyInitializationException
        // that would occur if we accessed ApplicationEntity.getIndividuals() outside a session.
        List<UUID> individualIds = jdbcTemplate.queryForList(
                "SELECT individual_id FROM linked_individuals WHERE application_id = ?",
                UUID.class,
                applicationId);
        for (UUID id : individualIds) {
            if (!trackedIndividualIds.contains(id)) {
                trackedIndividualIds.add(id);
            }
        }
    }

    /**
     * Flushes an already-tracked entity back to the database without re-registering it in the
     * tracking lists.  Use this for in-place mutations (e.g. setting a FK after both sides are
     * persisted) so that all writes go through this generator rather than directly via a repository.
     *
     * <p>Supported entity types: {@link ApplicationEntity}, {@link CaseworkerEntity},
     * {@link IndividualEntity}.
     *
     * @param entity the entity to save-and-flush; must already have been persisted
     * @return the entity (post-flush state), for fluent call sites
     * @throws IllegalArgumentException if the entity type has no mapped repository
     */
    @SuppressWarnings("unchecked")
    public <TEntity> TEntity updateAndFlush(TEntity entity) {
        if (entity instanceof ApplicationEntity e) {
            applicationContext.getBean(ApplicationRepository.class).saveAndFlush(e);
        } else if (entity instanceof CaseworkerEntity e) {
            applicationContext.getBean(CaseworkerRepository.class).saveAndFlush(e);
        } else if (entity instanceof IndividualEntity e) {
            applicationContext.getBean(IndividualRepository.class).saveAndFlush(e);
        } else {
            throw new IllegalArgumentException(
                    "No repository mapped for entity type: " + entity.getClass().getName());
        }
        // Entity is already tracked — no need to call track() again.
        return entity;
    }

    /**
     * Deletes every entity persisted via this generator, in leaf-to-root order
     * to respect FK constraints.  findById().ifPresent() makes each delete
     * idempotent — safe even if the test itself already deleted a row.
     * Call this from @AfterEach in BaseHarnessTest.
     *
     * <p>Note: since V14__change_decision_relationship.sql the FK runs
     * {@code applications.decision_id → decisions}, so deleting an application does NOT
     * cascade-delete its decision.  We therefore collect each tracked application's
     * {@code decision_id} via JDBC before deletion, then delete those decisions afterwards.
     */
    public void deleteTrackedData() {
        DomainEventRepository deRepo    = applicationContext.getBean(DomainEventRepository.class);
        ApplicationRepository appRepo   = applicationContext.getBean(ApplicationRepository.class);
        CaseworkerRepository cwRepo     = applicationContext.getBean(CaseworkerRepository.class);
        IndividualRepository indivRepo  = applicationContext.getBean(IndividualRepository.class);
        DecisionRepository decRepo      = applicationContext.getBean(DecisionRepository.class);

        try {
            // Discover decision_id for every tracked application before we delete the application row.
            // We read via JDBC to avoid lazy-load issues and to cope with the fact that the
            // application entity cached in memory may not reflect the latest decision_id.
            for (UUID appId : trackedApplicationIds) {
                List<UUID> decisionIds = jdbcTemplate.queryForList(
                        "SELECT decision_id FROM applications WHERE id = ? AND decision_id IS NOT NULL",
                        UUID.class, appId);
                for (UUID decId : decisionIds) {
                    if (!trackedDecisionIds.contains(decId)) {
                        trackedDecisionIds.add(decId);
                    }
                }
            }

            // ON DELETE CASCADE covers proceedings, merits_decisions via linked_merits_decisions, certificates
            trackedDomainEventIds.forEach(id -> deRepo.findById(id).ifPresent(deRepo::delete));
            trackedApplicationIds.forEach(id -> appRepo.findById(id).ifPresent(appRepo::delete));
            // Decisions must be deleted AFTER their parent application (the FK is application→decision,
            // so the application row must go first to clear the FK column before we can drop the decision).
            trackedDecisionIds.forEach(id -> decRepo.findById(id).ifPresent(decRepo::delete));
            trackedCaseworkerIds.forEach(id  -> cwRepo.findById(id).ifPresent(cwRepo::delete));
            trackedIndividualIds.forEach(id  -> indivRepo.findById(id).ifPresent(indivRepo::delete));
        } finally {
            clearTrackedIds();
        }
    }

    /** Clears tracking lists without deleting.  Call this if you need to reset mid-test. */
    public void clearTrackedIds() {
        trackedDomainEventIds.clear();
        trackedApplicationIds.clear();
        trackedDecisionIds.clear();
        trackedCaseworkerIds.clear();
        trackedIndividualIds.clear();
    }

    private void track(Object entity) {
        if (entity instanceof ApplicationEntity e) {
            trackedApplicationIds.add(e.getId());
            // Also track individuals cascade-persisted with this application
            if (e.getIndividuals() != null) {
                e.getIndividuals().stream()
                        .filter(ind -> ind.getId() != null)
                        .forEach(ind -> {
                            if (!trackedIndividualIds.contains(ind.getId())) {
                                trackedIndividualIds.add(ind.getId());
                            }
                        });
            }
        } else if (entity instanceof CaseworkerEntity e)   trackedCaseworkerIds.add(e.getId());
        else if (entity instanceof DomainEventEntity e)  trackedDomainEventIds.add(e.getId());
        else if (entity instanceof IndividualEntity e)   trackedIndividualIds.add(e.getId());
        else if (entity instanceof uk.gov.justice.laa.dstew.access.entity.DecisionEntity e) trackedDecisionIds.add(e.getId());
    }

    public <TEntity, TGenerator> TEntity persist(Class<TGenerator> generatorType, TEntity entity) {
        JpaRepository<TEntity, ?> repository = getRepository(generatorType);
        repository.saveAndFlush(entity);
        track(entity);
        return entity;
    }

    /**
     * Creates and persists a DecisionEntity where the meritsDecisions set contains already-persisted
     * (detached) MeritsDecisionEntity instances.  Uses merge to re-attach them within the same
     * transaction so that CascadeType.PERSIST does not throw DetachedEntityPassedToPersist.
     */
    @Transactional
    public uk.gov.justice.laa.dstew.access.entity.DecisionEntity createAndPersistWithPersistedMeritsDecisions(
            Consumer<uk.gov.justice.laa.dstew.access.entity.DecisionEntity.DecisionEntityBuilder> customiser) {
        uk.gov.justice.laa.dstew.access.entity.DecisionEntity entity =
                DataGenerator.createDefault(DecisionEntityGenerator.class, customiser);
        if (entity.getMeritsDecisions() != null) {
            java.util.Set<uk.gov.justice.laa.dstew.access.entity.MeritsDecisionEntity> merged = new java.util.HashSet<>();
            for (uk.gov.justice.laa.dstew.access.entity.MeritsDecisionEntity md : entity.getMeritsDecisions()) {
                merged.add(md.getId() != null ? entityManager.merge(md) : md);
            }
            entity.setMeritsDecisions(merged);
        }
        DecisionRepository decRepo = applicationContext.getBean(DecisionRepository.class);
        uk.gov.justice.laa.dstew.access.entity.DecisionEntity saved = decRepo.saveAndFlush(entity);
        track(saved);
        return saved;
    }

    /**
     * Creates and persists an ApplicationEntity where the individuals set contains already-persisted
     * (detached) IndividualEntity instances.  Uses merge to re-attach them within the same transaction
     * so that CascadeType.PERSIST does not throw DetachedEntityPassedToPersist.
     */
    @Transactional
    public ApplicationEntity createAndPersistWithPersistedIndividuals(
            Consumer<ApplicationEntity.ApplicationEntityBuilder> customiser) {
        ApplicationEntity entity = DataGenerator.createDefault(ApplicationEntityGenerator.class, customiser);
        if (entity.getIndividuals() != null) {
            java.util.Set<IndividualEntity> merged = new java.util.HashSet<>();
            for (IndividualEntity ind : entity.getIndividuals()) {
                merged.add(ind.getId() != null ? entityManager.merge(ind) : ind);
            }
            entity.setIndividuals(merged);
        }
        ApplicationRepository appRepo = applicationContext.getBean(ApplicationRepository.class);
        ApplicationEntity saved = appRepo.saveAndFlush(entity);
        track(saved);
        return saved;
    }

    public <TEntity, TGenerator> List<TEntity> persist(Class<TGenerator> generatorType, List<TEntity> entities) {
        JpaRepository<TEntity, ?> repository = getRepository(generatorType);
        repository.saveAllAndFlush(entities);
        entities.forEach(this::track);
        return entities;
    }

    /**
     * Persists a LinkedApplicationEntity using entityManager directly (no repository exists for this type).
     * The linked_applications row is cascade-deleted when the lead application is deleted via deleteTrackedData().
     * Must be called within a transaction — this method is @Transactional for that purpose.
     */
    @Transactional
    public void persistLink(ApplicationEntity leadApplication, ApplicationEntity associateApplication) {
        entityManager.persist(DataGenerator.createDefault(LinkedApplicationEntityGenerator.class, builder -> builder
                .leadApplicationId(leadApplication.getId())
                .associatedApplicationId(associateApplication.getId())
        ));
    }
}