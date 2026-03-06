package uk.gov.justice.laa.dstew.access.utils.factory.application;

import uk.gov.justice.laa.dstew.access.entity.LinkedApplicationEntity;
import uk.gov.justice.laa.dstew.access.utils.factory.Factory;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

public class LinkedApplicationEntityFactoryImpl
    implements Factory<LinkedApplicationEntity, LinkedApplicationEntity.LinkedApplicationEntityBuilder> {

  @Override
  public LinkedApplicationEntity create() {
    return LinkedApplicationEntity.builder()
        .leadApplicationId(UUID.randomUUID())
        .associatedApplicationId(UUID.randomUUID())
        .linkedAt(Instant.now())
        .build();
  }

  @Override
  public LinkedApplicationEntity create(
      Consumer<LinkedApplicationEntity.LinkedApplicationEntityBuilder> customiser) {

    LinkedApplicationEntity entity = create();
    LinkedApplicationEntity.LinkedApplicationEntityBuilder builder = entity.toBuilder();
    customiser.accept(builder);
    return builder.build();
  }
}
