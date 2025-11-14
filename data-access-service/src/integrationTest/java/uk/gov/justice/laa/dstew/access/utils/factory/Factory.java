package uk.gov.justice.laa.dstew.access.utils.factory;

import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;

import java.util.function.Consumer;

public interface Factory<TEntity, TBuilder> {
    TEntity create();
    TEntity customise(Consumer<TBuilder> customiser);
}