package uk.gov.justice.laa.dstew.access.utils.factory.proceeding;

import java.util.UUID;
import java.util.function.Consumer;
import uk.gov.justice.laa.dstew.access.model.Proceeding;
import uk.gov.justice.laa.dstew.access.utils.factory.Factory;

public class ProceedingRequestFactoryImpl implements Factory<Proceeding, Proceeding.ProceedingBuilder> {

  @Override
  public Proceeding create() {
    Proceeding proceeding = Proceeding.builder()
        .description("description")
        .leadProceeding(true)
        .usedDelegatedFunctions(true)
        .categoryOfLaw("FAMILY")
        .matterType("SCA")
        .id(UUID.randomUUID())
        .build();
    proceeding.putAdditionalProperty("test", "additionalProceedingProperty");
    return proceeding;

  }

  public Proceeding create(Consumer<Proceeding.ProceedingBuilder> customiser) {
    Proceeding proceeding = create();
    Proceeding.ProceedingBuilder builder = proceeding.toBuilder();
    customiser.accept(builder);
    return builder.build();
  }

}
