package uk.gov.justice.laa.dstew.access.utils.generator.domain;

import java.util.Map;
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
    return ProceedingDomain.builder()
        .id(UUID.randomUUID())
        .applyProceedingId(UUID.randomUUID())
        .description("Proceeding description")
        .isLead(true)
        .proceedingContent(
            Map.of(
                "id",
                UUID.randomUUID().toString(),
                "leadProceeding",
                true,
                "categoryOfLawEnum",
                "Family",
                "matterTypeEnum",
                "SPECIAL_CHILDREN_ACT"))
        .createdBy("")
        .updatedBy("")
        .build();
  }
}
