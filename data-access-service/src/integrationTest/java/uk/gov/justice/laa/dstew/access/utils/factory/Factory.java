package uk.gov.justice.laa.dstew.access.utils.factory;

import java.util.function.Consumer;

public interface Factory<TEntity, TBuilder> {
    TEntity create();
    TEntity customise(Consumer<TBuilder> customiser);
}