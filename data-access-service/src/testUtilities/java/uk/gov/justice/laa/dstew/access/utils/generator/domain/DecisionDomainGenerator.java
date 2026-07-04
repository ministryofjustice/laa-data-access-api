package uk.gov.justice.laa.dstew.access.utils.generator.domain;

import java.time.Instant;
import uk.gov.justice.laa.dstew.access.domain.DecisionDomain;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

/** Generator for {@link DecisionDomain} test instances. */
public class DecisionDomainGenerator
    extends BaseGenerator<DecisionDomain, DecisionDomain.DecisionDomainBuilder> {

  /** Constructs the generator. */
  public DecisionDomainGenerator() {
    super(DecisionDomain::toBuilder, DecisionDomain.DecisionDomainBuilder::build);
  }

  @Override
  public DecisionDomain createDefault() {
    return DecisionDomain.builder().overallDecision("REFUSED").modifiedAt(Instant.now()).build();
  }
}
