package uk.gov.justice.laa.dstew.access.utils.factory;

import java.util.function.Consumer;

public interface Factory<TEntity, TBuilder> {
    TEntity create();
    TEntity create(Consumer<TBuilder> customiser);

//    default TEntity customise(Consumer<TBuilder> customiser) {
//        TEntity entity = create();
//        TBuilder builder = entity.toBuilder();
//        customiser.accept(builder);
//        return builder.build();
//    }
}