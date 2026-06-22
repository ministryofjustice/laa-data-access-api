package uk.gov.justice.laa.dstew.access.utils.generator.getapplication;

import uk.gov.justice.laa.dstew.access.domain.OpponentDomain;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

public class OpponentDomainGenerator
    extends BaseGenerator<OpponentDomain, OpponentDomain.OpponentDomainBuilder> {

  public OpponentDomainGenerator() {
    super(OpponentDomain::toBuilder, OpponentDomain.OpponentDomainBuilder::build);
  }

  @Override
  public OpponentDomain createDefault() {
    return OpponentDomain.builder()
        .opponentType("ApplicationMeritsTask::Individual")
        .firstName("John")
        .lastName("Smith")
        .organisationName("Acme Ltd")
        .build();
  }
}
