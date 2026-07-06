package uk.gov.justice.laa.dstew.access.utils.generator.domain;

import java.util.UUID;
import uk.gov.justice.laa.dstew.access.domain.LinkedApplicationSummaryDomain;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

/** Generates {@link LinkedApplicationSummaryDomain} instances for use in tests. */
public class LinkedApplicationSummaryDomainGenerator
    extends BaseGenerator<
        LinkedApplicationSummaryDomain,
        LinkedApplicationSummaryDomain.LinkedApplicationSummaryDomainBuilder> {

  /** Constructs the generator. */
  public LinkedApplicationSummaryDomainGenerator() {
    super(
        LinkedApplicationSummaryDomain::toBuilder,
        LinkedApplicationSummaryDomain.LinkedApplicationSummaryDomainBuilder::build);
  }

  @Override
  public LinkedApplicationSummaryDomain createDefault() {
    return LinkedApplicationSummaryDomain.builder()
        .applicationId(UUID.randomUUID())
        .laaReference("REF7327")
        .isLead(false)
        .leadApplicationId(UUID.randomUUID())
        .build();
  }
}
