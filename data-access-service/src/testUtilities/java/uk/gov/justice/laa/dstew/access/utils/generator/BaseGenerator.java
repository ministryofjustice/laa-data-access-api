package uk.gov.justice.laa.dstew.access.utils.generator;

import net.datafaker.Faker;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public abstract class BaseGenerator<TEntity, TBuilder> {

    private final Function<TEntity, TBuilder> toBuilder;
    private final Function<TBuilder, TEntity> buildFromBuilder;

    protected Faker faker = new Faker(new java.util.Random(12345L));

    public BaseGenerator(
            Function<TEntity, TBuilder> toBuilder,
            Function<TBuilder, TEntity> buildFromBuilder) {
        this.toBuilder = toBuilder;
        this.buildFromBuilder = buildFromBuilder;
    }

    public abstract TEntity createDefault();

    public TEntity createRandom() {
        throw new UnsupportedOperationException("createRandom not implemented");
    }

    public TEntity createDefault(Consumer<TBuilder> customiser) {
        return create(this::createDefault, customiser);
    }

    public TEntity createRandom(Consumer<TBuilder> customiser) {
        return create(this::createRandom, customiser);
    }

    private TEntity create(Supplier<TEntity> creator, Consumer<TBuilder> customiser) {
        TEntity entity = creator.get();

        if (customiser == null) {
            return entity;
        }

        TBuilder builder = toBuilder.apply(entity);
        customiser.accept(builder);
        return buildFromBuilder.apply(builder);
    }

    public LocalDate getRandomDate() {
        Date from = Date.from(LocalDate.of(1950, 1, 1).atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date to = Date.from(LocalDate.of(LocalDate.now().getYear(), 12, 31).atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date randomDate = faker.date().between(from, to);
        return randomDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }
}