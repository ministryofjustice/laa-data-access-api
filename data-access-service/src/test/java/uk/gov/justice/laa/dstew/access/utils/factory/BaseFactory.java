package uk.gov.justice.laa.dstew.access.utils.factory;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.datafaker.Faker;
import org.springframework.context.annotation.Profile;

@Profile("unit-test")
public abstract class BaseFactory<TEntity, TBuilder> {

  private final Function<TEntity, TBuilder> toBuilder;
  private final Function<TBuilder, TEntity> buildFromBuilder;

  protected Faker faker = new Faker(new java.util.Random(12345L));

  public BaseFactory(
      Function<TEntity, TBuilder> toBuilder, Function<TBuilder, TEntity> buildFromBuilder) {
    this.toBuilder = toBuilder;
    this.buildFromBuilder = buildFromBuilder;
  }

  public abstract TEntity createDefault();

  public TEntity createRandom() {
    throw new UnsupportedOperationException("createRandom not implemented");
  }
  ;

  public TEntity createDefault(Consumer<TBuilder> customiser) {
    return create(this::createDefault, customiser);
  }

  public TEntity createRandom(Consumer<TBuilder> customiser) {
    return create(this::createRandom, customiser);
  }

  public List<TEntity> createMultipleDefault(int count) {
    return createMultiple(this::createDefault, count, null);
  }

  public List<TEntity> createMultipleRandom(int count) {
    return createMultiple(this::createRandom, count, null);
  }

  public List<TEntity> createMultipleDefault(int count, Consumer<TBuilder> customiser) {
    return createMultiple(this::createDefault, count, customiser);
  }

  public List<TEntity> createMultipleRandom(int count, Consumer<TBuilder> customiser) {
    return createMultiple(this::createRandom, count, customiser);
  }

  public LocalDate getRandomDate() {
    Date from =
        Date.from(LocalDate.of(1950, 1, 1).atStartOfDay(ZoneId.systemDefault()).toInstant());
    Date to =
        Date.from(
            LocalDate.of(LocalDate.now().getYear(), 12, 31)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant());
    Date randomDate = faker.date().between(from, to);
    return randomDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
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

  private List<TEntity> createMultiple(
      Supplier<TEntity> creator, int count, Consumer<TBuilder> customiser) {
    List<TEntity> entities = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      entities.add(create(creator, customiser));
    }
    return entities;
  }
}
