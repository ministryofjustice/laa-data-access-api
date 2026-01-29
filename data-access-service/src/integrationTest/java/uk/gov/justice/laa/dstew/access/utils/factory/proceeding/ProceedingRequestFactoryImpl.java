package uk.gov.justice.laa.dstew.access.utils.factory.proceeding;

import java.util.UUID;
import java.util.function.Consumer;
import uk.gov.justice.laa.dstew.access.model.Proceeding;
import uk.gov.justice.laa.dstew.access.utils.factory.Factory;

public class ProceedingRequestFactoryImpl implements Factory<Proceeding, Proceeding.Builder> {

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

  public Proceeding create(Consumer<Proceeding.Builder> customiser) {
    Proceeding proceeding = create();
    Proceeding.Builder builder = proceeding.toBuilder();
    customiser.accept(builder);
    return builder.build();
  }

}
