package uk.gov.justice.laa.dstew.access.utils.generator.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.domain.ApplicationDomain;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationContentGenerator;

public class ApplicationDomainGenerator
    extends BaseGenerator<ApplicationDomain, ApplicationDomain.ApplicationDomainBuilder> {

  private final IndividualDomainGenerator individualDomainGenerator =
      new IndividualDomainGenerator();
  private final ProceedingDomainGenerator proceedingDomainGenerator =
      new ProceedingDomainGenerator();

  public ApplicationDomainGenerator() {
    super(ApplicationDomain::toBuilder, ApplicationDomain.ApplicationDomainBuilder::build);
  }

  @Override
  public ApplicationDomain createDefault() {
    ObjectMapper mapper = new ObjectMapper();
    ApplicationContentGenerator contentGen = new ApplicationContentGenerator();

    @SuppressWarnings("unchecked")
    Map<String, Object> appContent = mapper.convertValue(contentGen.createDefault(), Map.class);

    return ApplicationDomain.builder()
        .id(UUID.randomUUID()) // post-save state: id populated
        .status("APPLICATION_IN_PROGRESS")
        .laaReference("REF7327")
        .officeCode("OFFICE001")
        .applicationContent(appContent)
        .individuals(Set.of(individualDomainGenerator.createDefault()))
        .schemaVersion(1)
        .createdAt(Instant.now()) // post-save state: createdAt populated
        .modifiedAt(Instant.now())
        .applyApplicationId(UUID.randomUUID())
        .submittedAt(Instant.parse("2024-01-01T12:00:00Z"))
        .usedDelegatedFunctions(false)
        .categoryOfLaw("FAMILY")
        .matterType("SPECIAL_CHILDREN_ACT")
        .isAutoGranted(false)
        .proceedings(Set.of(proceedingDomainGenerator.createDefault()))
        .build();
  }
}
