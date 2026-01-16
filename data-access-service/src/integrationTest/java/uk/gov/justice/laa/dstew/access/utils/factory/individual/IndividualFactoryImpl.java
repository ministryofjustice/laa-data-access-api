package uk.gov.justice.laa.dstew.access.utils.factory.individual;

import java.time.LocalDate;
import java.util.Map;
import java.util.function.Consumer;
import uk.gov.justice.laa.dstew.access.model.Individual;
import uk.gov.justice.laa.dstew.access.model.IndividualType;
import uk.gov.justice.laa.dstew.access.utils.factory.Factory;

public class IndividualFactoryImpl implements Factory<Individual, Individual.Builder> {
  @Override
  public Individual create() {
    return Individual.builder()
        .firstName("John")
        .lastName("Doe")
        .dateOfBirth(LocalDate.now())
        .details(Map.of("test", "content"))
        .type(IndividualType.CLIENT)
        .build();
  }

  @Override
  public Individual create(Consumer<Individual.Builder> customiser) {
    Individual individual = create();
    Individual.Builder builder = individual.toBuilder();
    customiser.accept(builder);
    return builder.build();
  }
}
