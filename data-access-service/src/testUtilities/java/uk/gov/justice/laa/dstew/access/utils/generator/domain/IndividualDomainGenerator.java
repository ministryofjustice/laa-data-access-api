package uk.gov.justice.laa.dstew.access.utils.generator.domain;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.domain.IndividualDomain;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

public class IndividualDomainGenerator
    extends BaseGenerator<IndividualDomain, IndividualDomain.IndividualDomainBuilder> {

  public IndividualDomainGenerator() {
    super(IndividualDomain::toBuilder, IndividualDomain.IndividualDomainBuilder::build);
  }

  @Override
  public IndividualDomain createDefault() {
    return IndividualDomain.builder()
        .id(UUID.randomUUID())
        .firstName("John")
        .lastName("Doe")
        .dateOfBirth(LocalDate.of(1990, 1, 1))
        .individualContent(Map.of("test", "content"))
        .type("CLIENT")
        .build();
  }
}
