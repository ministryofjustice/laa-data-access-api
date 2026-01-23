package uk.gov.justice.laa.dstew.access.utils.generator;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.repository.DomainEventRepository;
import uk.gov.justice.laa.dstew.access.utils.generator.domainEvent.DomainEventGenerator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Component
public class PersistedDataGenerator extends DataGenerator {

    @Autowired
    private ApplicationContext applicationContext;

    // Map generator class to repository class
    private final Map<Class<?>, Class<? extends JpaRepository<?, ?>>> generatorRepoMap = new HashMap<>();

    @PostConstruct
    public void init() {
        registerRepository(DomainEventGenerator.class, DomainEventRepository.class);
    }

    public <TGenerator, TRepository extends JpaRepository<?, ?>>
    void registerRepository(Class<TGenerator> generatorType, Class<TRepository> repositoryType) {
        generatorRepoMap.put(generatorType, repositoryType);
    }

    public <TEntity, TBuilder, TGenerator extends BaseGenerator<TEntity, TBuilder>>
    TEntity createAndPersist(Class<TGenerator> generatorType) {
        TEntity entity = DataGenerator.createDefault(generatorType);
        JpaRepository<TEntity, ?> repository = getRepository(generatorType);
        return repository.saveAndFlush(entity);
    }

    public <TEntity, TBuilder, TGenerator extends BaseGenerator<TEntity, TBuilder>>
    TEntity createAndPersist(Class<TGenerator> generatorType, Consumer<TBuilder> customiser) {
        TEntity entity = DataGenerator.createDefault(generatorType, customiser);
        JpaRepository<TEntity, ?> repository = getRepository(generatorType);
        return repository.saveAndFlush(entity);
    }

    public <TEntity, TBuilder, TGenerator extends BaseGenerator<TEntity, TBuilder>>
    List<TEntity> createAndPersistMultiple(Class<TGenerator> generatorType, int count) {
        List<TEntity> entities = DataGenerator.createMultipleDefault(generatorType, count);
        if (entities.isEmpty()) {
            return entities;
        }
        JpaRepository<TEntity, ?> repository = getRepository(generatorType);
        return repository.saveAllAndFlush(entities);
    }

    private <TEntity, TGenerator> JpaRepository<TEntity, ?> getRepository(Class<TGenerator> generatorType) {
        Class<? extends JpaRepository<?, ?>> repoClass = generatorRepoMap.get(generatorType);
        if (repoClass == null) {
            throw new IllegalArgumentException("No repository registered for generator: " + generatorType.getName());
        }
        return (JpaRepository<TEntity, ?>) applicationContext.getBean(repoClass);
    }
}