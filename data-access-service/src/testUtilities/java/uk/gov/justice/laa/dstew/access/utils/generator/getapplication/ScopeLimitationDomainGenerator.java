package uk.gov.justice.laa.dstew.access.utils.generator.getapplication;

import uk.gov.justice.laa.dstew.access.domain.ScopeLimitationDomain;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

public class ScopeLimitationDomainGenerator
    extends BaseGenerator<
        ScopeLimitationDomain, ScopeLimitationDomain.ScopeLimitationDomainBuilder> {

  public ScopeLimitationDomainGenerator() {
    super(
        ScopeLimitationDomain::toBuilder,
        ScopeLimitationDomain.ScopeLimitationDomainBuilder::build);
  }

  @Override
  public ScopeLimitationDomain createDefault() {
    return ScopeLimitationDomain.builder()
        .scopeLimitation("CV027")
        .scopeDescription("Limitation of costs applies")
        .build();
  }
}
