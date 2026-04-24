package uk.gov.justice.laa.dstew.access.utils.generator.proceeding;

import java.util.UUID;
import uk.gov.justice.laa.dstew.access.domain.ProceedingDomain;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

public class ProceedingDomainGenerator
    extends BaseGenerator<ProceedingDomain, ProceedingDomain.ProceedingDomainBuilder> {

  public ProceedingDomainGenerator() {
    super(ProceedingDomain::toBuilder, ProceedingDomain.ProceedingDomainBuilder::build);
  }

  @Override
  public ProceedingDomain createDefault() {
    String id = UUID.randomUUID().toString();
    return ProceedingDomain.builder()
        .id(UUID.randomUUID())
        .applyProceedingId(UUID.randomUUID())
        .description("Test proceeding")
        .isLead(true)
        .proceedingContent(
            "{\"id\":\""
                + id
                + "\",\"leadProceeding\":true,\"usedDelegatedFunctions\":false,"
                + "\"categoryOfLaw\":\"FAMILY\",\"matterType\":\"SPECIAL_CHILDREN_ACT\","
                + "\"description\":\"Test proceeding\"}")
        .meritsDecision(null)
        .build();
  }
}
