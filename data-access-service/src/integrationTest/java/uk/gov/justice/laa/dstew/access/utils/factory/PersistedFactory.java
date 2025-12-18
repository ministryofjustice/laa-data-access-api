package uk.gov.justice.laa.dstew.access.utils.factory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

public class PersistedFactory<
        TRepository extends JpaRepository<TEntity, TId>,
        TFactory extends Factory<TEntity, TBuilder>,
        TEntity,
        TBuilder,
        TId> {

    private final TRepository repository;

    private final TFactory factory;

    public PersistedFactory(TRepository repository, TFactory factory) {
        this.repository = repository;
        this.factory = factory;
    }

    public void persist(TEntity entity) {
        repository.save(entity);
    }

    public TEntity createAndPersist() {
        TEntity entity = factory.create();
        persist(entity);
        return entity;
    }

    public TEntity createAndPersist(Consumer<TBuilder> customiser) {
        TEntity entity = factory.create(customiser);
        persist(entity);
        return entity;
    }

    public void persistMultiple(List<TEntity> entities) {
        repository.saveAll(entities);
    }

    public List<TEntity> createMultiple(int number, Consumer<TBuilder> customiser) {
        List<TEntity> entities = new LinkedList<>();
        for (int i = 0; i < number; i++) {
            TEntity entity = factory.create(customiser);
            entities.add(entity);
        }

        return entities;
    }

    public List<TEntity> createAndPersistMultiple(int number, Consumer<TBuilder> customiser) {
        List<TEntity> entities = createMultiple(number, customiser);
        persistMultiple(entities);
        return entities;
    }
}