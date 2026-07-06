package uk.gov.justice.laa.dstew.access.utils.generator.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.domain.ApplicationSummaryDomain;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

/** Generates {@link ApplicationSummaryDomain} instances for use in tests. */
public class ApplicationSummaryDomainGenerator
    extends BaseGenerator<
        ApplicationSummaryDomain, ApplicationSummaryDomain.ApplicationSummaryDomainBuilder> {

  /** Constructs the generator. */
  public ApplicationSummaryDomainGenerator() {
    super(
        ApplicationSummaryDomain::toBuilder,
        ApplicationSummaryDomain.ApplicationSummaryDomainBuilder::build);
  }

  @Override
  public ApplicationSummaryDomain createDefault() {
    return ApplicationSummaryDomain.builder()
        .id(UUID.randomUUID())
        .submittedAt(Instant.now())
        .isAutoGranted(false)
        .categoryOfLaw("FAMILY")
        .matterType("SPECIAL_CHILDREN_ACT")
        .usedDelegatedFunctions(false)
        .laaReference("REF7327")
        .officeCode("1A234B")
        .status("APPLICATION_IN_PROGRESS")
        .caseworkerId(UUID.randomUUID())
        .clientFirstName("Jane")
        .clientLastName("Doe")
        .clientDateOfBirth(LocalDate.of(1990, 1, 1))
        .applicationType("INITIAL")
        .modifiedAt(Instant.now())
        .isLead(true)
        .linkedApplications(List.of())
        .build();
  }
}
