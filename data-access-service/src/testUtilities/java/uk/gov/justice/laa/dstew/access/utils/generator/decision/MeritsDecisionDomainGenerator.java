package uk.gov.justice.laa.dstew.access.utils.generator.decision;

import java.time.Instant;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.domain.MeritsDecisionDomain;
import uk.gov.justice.laa.dstew.access.domain.MeritsDecisionOutcome;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

public class MeritsDecisionDomainGenerator
    extends BaseGenerator<MeritsDecisionDomain, MeritsDecisionDomain.MeritsDecisionDomainBuilder> {

  public MeritsDecisionDomainGenerator() {
    super(MeritsDecisionDomain::toBuilder, MeritsDecisionDomain.MeritsDecisionDomainBuilder::build);
  }

  @Override
  public MeritsDecisionDomain createDefault() {
    return MeritsDecisionDomain.builder()
        .id(UUID.randomUUID())
        .decision(MeritsDecisionOutcome.REFUSED)
        .reason("Test reason")
        .justification("Test justification")
        .modifiedAt(Instant.now())
        .build();
  }
}
