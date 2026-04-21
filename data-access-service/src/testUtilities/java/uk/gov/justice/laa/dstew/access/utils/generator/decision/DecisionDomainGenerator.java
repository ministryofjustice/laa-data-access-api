package uk.gov.justice.laa.dstew.access.utils.generator.decision;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.domain.DecisionDomain;
import uk.gov.justice.laa.dstew.access.domain.OverallDecisionStatus;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

public class DecisionDomainGenerator
    extends BaseGenerator<DecisionDomain, DecisionDomain.DecisionDomainBuilder> {

  private final MeritsDecisionDomainGenerator meritsGenerator = new MeritsDecisionDomainGenerator();

  public DecisionDomainGenerator() {
    super(DecisionDomain::toBuilder, DecisionDomain.DecisionDomainBuilder::build);
  }

  @Override
  public DecisionDomain createDefault() {
    return DecisionDomain.builder()
        .id(UUID.randomUUID())
        .overallDecision(OverallDecisionStatus.REFUSED)
        .meritsDecisions(Set.of(meritsGenerator.createDefault()))
        .modifiedAt(Instant.now())
        .build();
  }
}
