package uk.gov.justice.laa.dstew.access.utils.generator;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class DataGenerator {

    public static <TEntity, TBuilder, TGenerator extends BaseGenerator<TEntity, TBuilder>>
    TEntity createDefault(Class<TGenerator> generatorType) {
        try {
            TGenerator generator = generatorType.getDeclaredConstructor().newInstance();
            return generator.createDefault();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create default entity", e);
        }
    }

    public static <TEntity, TBuilder, TGenerator extends BaseGenerator<TEntity, TBuilder>>
    TEntity createRandom(Class<TGenerator> generatorType) {
        try {
            TGenerator generator = generatorType.getDeclaredConstructor().newInstance();
            return generator.createRandom();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create random entity", e);
        }
    }

    public static <TEntity, TBuilder, TGenerator extends BaseGenerator<TEntity, TBuilder>>
    TEntity createDefault(Class<TGenerator> generatorType, Consumer<TBuilder> customiser) {
        try {
            TGenerator generator = generatorType.getDeclaredConstructor().newInstance();
            return generator.createDefault(customiser);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create default entity with customiser", e);
        }
    }

    public static <TEntity, TBuilder, TGenerator extends BaseGenerator<TEntity, TBuilder>>
    TEntity createRandom(Class<TGenerator> generatorType, Consumer<TBuilder> customiser) {
        try {
            TGenerator generator = generatorType.getDeclaredConstructor().newInstance();
            return generator.createRandom(customiser);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create random entity with customiser", e);
        }
    }

    public static <TEntity, TBuilder, TGenerator extends BaseGenerator<TEntity, TBuilder>>
    List<TEntity> createMultipleDefault(Class<TGenerator> generatorType, int count) {
        List<TEntity> entities = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            entities.add(createDefault(generatorType));
        }
        return entities;
    }

    public static <TEntity, TBuilder, TGenerator extends BaseGenerator<TEntity, TBuilder>>
    List<TEntity> createMultipleRandom(Class<TGenerator> generatorType, int count) {
        List<TEntity> entities = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            entities.add(createRandom(generatorType));
        }
        return entities;
    }

    public static <TEntity, TBuilder, TGenerator extends BaseGenerator<TEntity, TBuilder>>
    List<TEntity> createMultipleDefault(Class<TGenerator> generatorType, int count, Consumer<TBuilder> customiser) {
        List<TEntity> entities = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            entities.add(createDefault(generatorType, customiser));
        }
        return entities;
    }

    public static <TEntity, TBuilder, TGenerator extends BaseGenerator<TEntity, TBuilder>>
    List<TEntity> createMultipleRandom(Class<TGenerator> generatorType, int count, Consumer<TBuilder> customiser) {
        List<TEntity> entities = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            entities.add(createRandom(generatorType, customiser));
        }
        return entities;
    }
}