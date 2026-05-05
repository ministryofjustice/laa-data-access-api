package uk.gov.justice.laa.dstew.access.utils.generator.individual;

import java.time.LocalDate;
import java.util.Map;
import uk.gov.justice.laa.dstew.access.domain.Individual;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

public class IndividualDomainGenerator
    extends BaseGenerator<Individual, Individual.IndividualBuilder> {

  public IndividualDomainGenerator() {
    super(Individual::toBuilder, Individual.IndividualBuilder::build);
  }

  @Override
  public Individual createDefault() {
    return Individual.builder()
        .firstName("John")
        .lastName("Doe")
        .dateOfBirth(LocalDate.of(1980, 1, 1))
        .details(Map.of("test", "content"))
        .type("CLIENT")
        .build();
  }
}
