package uk.gov.justice.laa.dstew.access.utils.generator;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.repository.*;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationSummaryGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.caseworker.CaseworkerGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.decision.DecisionEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.domainEvent.DomainEventGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.merit.MeritsDecisionsEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.proceeding.ProceedingsEntityGenerator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Component
public class PersistedDataGenerator extends DataGenerator {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private EntityManager entityManager;

    // Map generator class to repository class
    private final Map<Class<?>, Class<? extends JpaRepository<?, ?>>> generatorRepoMap = new HashMap<>();

    @PostConstruct
    public void init() {
        registerRepository(DomainEventGenerator.class, DomainEventRepository.class);
        registerRepository(ApplicationEntityGenerator.class, ApplicationRepository.class);
//        registerRepository(ApplicationSummaryGenerator.class, ApplicationSummaryRepository.class);
        registerRepository(CaseworkerGenerator.class, CaseworkerRepository.class);
        registerRepository(DecisionEntityGenerator.class, DecisionRepository.class);
        registerRepository(ProceedingsEntityGenerator.class, ProceedingRepository.class);
        registerRepository(MeritsDecisionsEntityGenerator.class, MeritsDecisionRepository.class);
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

    private <TEntity, TGenerator> TEntity persist(Class<TGenerator> generatorType, TEntity entity) {
        JpaRepository<TEntity, ?> repository = getRepository(generatorType);
        repository.saveAndFlush(entity);
        return entity;
    }

    private <TEntity, TGenerator> List<TEntity> persist(Class<TGenerator> generatorType, List<TEntity> entities) {
        JpaRepository<TEntity, ?> repository = getRepository(generatorType);
        repository.saveAllAndFlush(entities);
        return entities;
    }
}