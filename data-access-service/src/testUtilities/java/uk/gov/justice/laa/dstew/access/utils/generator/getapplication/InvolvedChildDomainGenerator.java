package uk.gov.justice.laa.dstew.access.utils.generator.getapplication;

import java.time.LocalDate;
import uk.gov.justice.laa.dstew.access.domain.InvolvedChildDomain;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

public class InvolvedChildDomainGenerator
    extends BaseGenerator<InvolvedChildDomain, InvolvedChildDomain.InvolvedChildDomainBuilder> {

  public InvolvedChildDomainGenerator() {
    super(InvolvedChildDomain::toBuilder, InvolvedChildDomain.InvolvedChildDomainBuilder::build);
  }

  @Override
  public InvolvedChildDomain createDefault() {
    return InvolvedChildDomain.builder()
        .fullName("John Smith")
        .dateOfBirth(LocalDate.of(2022, 8, 20))
        .build();
  }
}
