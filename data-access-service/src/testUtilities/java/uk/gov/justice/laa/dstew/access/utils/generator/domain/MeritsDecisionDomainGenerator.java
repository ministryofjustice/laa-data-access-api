package uk.gov.justice.laa.dstew.access.utils.generator.domain;

import java.time.Instant;
import uk.gov.justice.laa.dstew.access.domain.MeritsDecisionDomain;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

/** Generator for {@link MeritsDecisionDomain} test instances. */
public class MeritsDecisionDomainGenerator
    extends BaseGenerator<MeritsDecisionDomain, MeritsDecisionDomain.MeritsDecisionDomainBuilder> {

  /** Constructs the generator. */
  public MeritsDecisionDomainGenerator() {
    super(MeritsDecisionDomain::toBuilder, MeritsDecisionDomain.MeritsDecisionDomainBuilder::build);
  }

  @Override
  public MeritsDecisionDomain createDefault() {
    return MeritsDecisionDomain.builder()
        .decision("GRANTED")
        .reason("default reason")
        .justification("default justification")
        .modifiedAt(Instant.now())
        .build();
  }
}
