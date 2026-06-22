package uk.gov.justice.laa.dstew.access.utils.generator.getapplication;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.domain.ApplicationProceedingDomain;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

public class ApplicationProceedingDomainGenerator
    extends BaseGenerator<
        ApplicationProceedingDomain,
        ApplicationProceedingDomain.ApplicationProceedingDomainBuilder> {

  private final InvolvedChildDomainGenerator involvedChildDomainGenerator =
      new InvolvedChildDomainGenerator();
  private final ScopeLimitationDomainGenerator scopeLimitationDomainGenerator =
      new ScopeLimitationDomainGenerator();

  public ApplicationProceedingDomainGenerator() {
    super(
        ApplicationProceedingDomain::toBuilder,
        ApplicationProceedingDomain.ApplicationProceedingDomainBuilder::build);
  }

  @Override
  public ApplicationProceedingDomain createDefault() {
    return ApplicationProceedingDomain.builder()
        .proceedingId(UUID.randomUUID())
        .description("Test proceeding")
        .proceedingType("hearing")
        .categoryOfLaw("FAMILY")
        .matterType("SPECIAL_CHILDREN_ACT")
        .levelOfService("Full representation")
        .substantiveCostLimitation(1350.0)
        .delegatedFunctionsDate(LocalDate.of(2025, 5, 6))
        .meritsDecision("REFUSED")
        .involvedChildren(List.of(involvedChildDomainGenerator.createDefault()))
        .scopeLimitations(List.of(scopeLimitationDomainGenerator.createDefault()))
        .build();
  }
}
