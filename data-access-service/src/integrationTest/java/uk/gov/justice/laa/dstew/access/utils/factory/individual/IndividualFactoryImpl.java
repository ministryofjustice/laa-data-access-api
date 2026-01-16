package uk.gov.justice.laa.dstew.access.utils.factory.individual;

import java.time.LocalDate;
import java.util.Map;
import java.util.function.Consumer;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.utils.factory.Factory;

public class IndividualFactoryImpl
    implements Factory<IndividualEntity, IndividualEntity.IndividualEntityBuilder> {
  @Override
  public IndividualEntity create() {
    return IndividualEntity.builder()
        .firstName("John")
        .lastName("Doe")
        .dateOfBirth(LocalDate.now())
        .individualContent(Map.of("test", "content"))
        .build();
  }

  @Override
  public IndividualEntity create(Consumer<IndividualEntity.IndividualEntityBuilder> customiser) {
    IndividualEntity entity = create();
    IndividualEntity.IndividualEntityBuilder builder = entity.toBuilder();
    customiser.accept(builder);
    return builder.build();
  }
}
