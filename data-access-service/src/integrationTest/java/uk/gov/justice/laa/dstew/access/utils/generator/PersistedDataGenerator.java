package uk.gov.justice.laa.dstew.access.utils.generator;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.repository.*;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationSummaryGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.LinkedApplicationEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.LinkedApplicationsGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.caseworker.CaseworkerGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.certificate.CertificateEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.decision.DecisionEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.domainEvent.DomainEventGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.individual.IndividualEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.merit.MeritsDecisionsEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.proceeding.ProceedingsEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.individual.IndividualGenerator;

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

    // Map generator class to repository class
    private final Map<Class<?>, Class<? extends JpaRepository<?, ?>>> generatorRepoMap = new HashMap<>();

    // Tracking lists — populated automatically on every persist so that
    // deleteTrackedData() can clean up exactly what this generator created.
    private final List<UUID> trackedCaseworkerIds  = new ArrayList<>();
    private final List<UUID> trackedApplicationIds = new ArrayList<>();
    private final List<UUID> trackedDomainEventIds = new ArrayList<>();

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
     * Deletes every entity persisted via this generator, in leaf-to-root order
     * to respect FK constraints.  findById().ifPresent() makes each delete
     * idempotent — safe even if the test itself already deleted a row.
     * Call this from @AfterEach in BaseHarnessTest.
     */
    public void deleteTrackedData() {
        DomainEventRepository deRepo  = applicationContext.getBean(DomainEventRepository.class);
        ApplicationRepository appRepo = applicationContext.getBean(ApplicationRepository.class);
        CaseworkerRepository cwRepo   = applicationContext.getBean(CaseworkerRepository.class);

        try {
            // ON DELETE CASCADE covers proceedings, decisions, merits_decisions, certificates
            trackedDomainEventIds.forEach(id -> deRepo.findById(id).ifPresent(deRepo::delete));
            trackedApplicationIds.forEach(id -> appRepo.findById(id).ifPresent(appRepo::delete));
            trackedCaseworkerIds.forEach(id  -> cwRepo.findById(id).ifPresent(cwRepo::delete));
        } finally {
            // Always clear — even if a delete threw — so stale IDs never carry into
            // the next test.  Any rows that weren't deleted will be caught by the
            // defensive deleteTrackedData() call in the next test's @BeforeEach.
            clearTrackedIds();
        }
    }

    /** Clears tracking lists without deleting.  Call this if you need to reset mid-test. */
    public void clearTrackedIds() {
        trackedDomainEventIds.clear();
        trackedApplicationIds.clear();
        trackedCaseworkerIds.clear();
    }

    private void track(Object entity) {
        if (entity instanceof ApplicationEntity e)   trackedApplicationIds.add(e.getId());
        else if (entity instanceof CaseworkerEntity e)  trackedCaseworkerIds.add(e.getId());
        else if (entity instanceof DomainEventEntity e) trackedDomainEventIds.add(e.getId());
    }

    public <TEntity, TGenerator> TEntity persist(Class<TGenerator> generatorType, TEntity entity) {
        JpaRepository<TEntity, ?> repository = getRepository(generatorType);
        repository.saveAndFlush(entity);
        track(entity);
        return entity;
    }

    public <TEntity, TGenerator> List<TEntity> persist(Class<TGenerator> generatorType, List<TEntity> entities) {
        JpaRepository<TEntity, ?> repository = getRepository(generatorType);
        repository.saveAllAndFlush(entities);
        entities.forEach(this::track);
        return entities;
    }
}