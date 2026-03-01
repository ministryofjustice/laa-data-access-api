package uk.gov.justice.laa.dstew.access.utils.factory;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

public class PersistedDynamoDbFactory<
        TFactory extends DynamoDbFactory<TEntity, TBuilder>,
        TEntity,
        TBuilder> {

    private final DynamoDbEnhancedClient dynamoDbEnhancedClient;
    private final TFactory factory;
    private final Class<TEntity> entityClass;
    private final String tableName;

    public PersistedDynamoDbFactory(DynamoDbEnhancedClient dynamoDbEnhancedClient, TFactory factory, Class<TEntity> entityClass, String tableName) {
        this.dynamoDbEnhancedClient = dynamoDbEnhancedClient;
        this.factory = factory;
        this.entityClass = entityClass;
        this.tableName = tableName;
    }

    public void persist(TEntity entity) {
        DynamoDbTable<TEntity> table = dynamoDbEnhancedClient.table(tableName, TableSchema.fromBean(entityClass));
        table.putItem(entity);
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
        DynamoDbTable<TEntity> table = dynamoDbEnhancedClient.table(tableName, TableSchema.fromBean(entityClass));
        for (TEntity entity : entities) {
            table.putItem(entity);
        }
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
