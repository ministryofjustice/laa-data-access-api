package uk.gov.justice.laa.dstew.access.utils.generator;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.repository.DomainEventRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Component
public class PersistedDataGenerator extends DataGenerator {

    @Autowired
    private ApplicationContext applicationContext;

    private final Map<Class<?>, Class<? extends JpaRepository<?, ?>>> entityRepoMap = new HashMap<>();

    @PostConstruct
    public void init() {
        registerRepository(DomainEventEntity.class, DomainEventRepository.class);
    }

    public <TEntity, TRepository extends JpaRepository<TEntity, ?>>
    void registerRepository(Class<TEntity> entityType, Class<TRepository> repositoryType) {
        entityRepoMap.put(entityType, repositoryType);
    }

    public <TEntity, TBuilder, TGenerator extends BaseGenerator<TEntity, TBuilder>>
    TEntity createAndPersist(Class<TGenerator> generatorType) {
        TEntity entity = DataGenerator.createDefault(generatorType);
        JpaRepository<TEntity, ?> repository = getRepository(entity.getClass());
        return repository.save(entity);
    }

    public <TEntity, TBuilder, TGenerator extends BaseGenerator<TEntity, TBuilder>>
    TEntity createAndPersist(Class<TGenerator> generatorType, Consumer<TBuilder> customiser) {
        TEntity entity = DataGenerator.createDefault(generatorType, customiser);
        JpaRepository<TEntity, ?> repository = getRepository(entity.getClass());
        return repository.save(entity);
    }

    public <TEntity, TBuilder, TGenerator extends BaseGenerator<TEntity, TBuilder>>
    List<TEntity> createAndPersistMultiple(Class<TGenerator> generatorType, int count) {
        List<TEntity> entities = DataGenerator.createMultipleDefault(generatorType, count);
        if (entities.isEmpty()) {
            return entities;
        }
        JpaRepository<TEntity, ?> repository = getRepository(entities.get(0).getClass());
        return repository.saveAll(entities);
    }

    private <TEntity> JpaRepository<TEntity, ?> getRepository(Class<?> entityClass) {
        Class<? extends JpaRepository<?, ?>> repoClass = entityRepoMap.get(entityClass);
        if (repoClass == null) {
            throw new IllegalArgumentException("No repository registered for entity: " + entityClass.getName());
        }
        return (JpaRepository<TEntity, ?>) applicationContext.getBean(repoClass);
    }
}