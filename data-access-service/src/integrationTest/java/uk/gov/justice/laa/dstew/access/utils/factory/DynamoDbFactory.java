package uk.gov.justice.laa.dstew.access.utils.factory;

import java.util.function.Consumer;

public interface DynamoDbFactory<TEntity, TBuilder> {
    TEntity create();
    TEntity create(Consumer<TBuilder> customiser);
}
