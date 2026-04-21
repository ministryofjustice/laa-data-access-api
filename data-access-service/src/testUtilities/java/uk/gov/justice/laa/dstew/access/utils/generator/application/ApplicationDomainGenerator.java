package uk.gov.justice.laa.dstew.access.utils.generator.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.domain.ApplicationDomain;
import uk.gov.justice.laa.dstew.access.domain.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.domain.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.domain.MatterType;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

public class ApplicationDomainGenerator
    extends BaseGenerator<ApplicationDomain, ApplicationDomain.ApplicationDomainBuilder> {

  public ApplicationDomainGenerator() {
    super(ApplicationDomain::toBuilder, ApplicationDomain.ApplicationDomainBuilder::build);
  }

  @Override
  public ApplicationDomain createDefault() {
    return ApplicationDomain.builder()
        .id(UUID.randomUUID())
        .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
        .laaReference("REF7327")
        .officeCode("1J001D")
        .applyApplicationId(UUID.randomUUID())
        .usedDelegatedFunctions(false)
        .categoryOfLaw(CategoryOfLaw.FAMILY)
        .matterType(MatterType.SPECIAL_CHILDREN_ACT)
        .submittedAt(Instant.parse("2024-01-01T12:00:00Z"))
        .createdAt(Instant.now())
        .applicationContent(Map.of())
        .individuals(List.of())
        .schemaVersion(1)
        .build();
  }
}
