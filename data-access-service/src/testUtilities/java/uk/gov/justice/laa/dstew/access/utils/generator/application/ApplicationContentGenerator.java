package uk.gov.justice.laa.dstew.access.utils.generator.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.model.ApplicationApplicant;
import uk.gov.justice.laa.dstew.access.model.ApplicationContent;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.proceeding.ProceedingGenerator;

public class ApplicationContentGenerator
    extends BaseGenerator<ApplicationContent, ApplicationContent.ApplicationContentBuilder> {
  private final ProceedingGenerator proceedingDtoGenerator = new ProceedingGenerator();
  private final ApplicationMeritsGenerator meritsGenerator = new ApplicationMeritsGenerator();
  private final ApplicationOfficeGenerator officeGenerator = new ApplicationOfficeGenerator();

  public ApplicationContentGenerator() {
    super(ApplicationContent::toBuilder, ApplicationContent.ApplicationContentBuilder::build);
  }

  @Override
  public ApplicationContent createDefault() {
    UUID applicationId = UUID.randomUUID();
    return ApplicationContent.builder()
        .id(applicationId)
        .office(officeGenerator.createDefault())
        .submittedAt("2024-01-01T12:00:00Z")
        .previousApplicationId("ZZ999Z")
        .lastNameAtBirth("Alberts")
        .correspondenceAddressType("Home")
        .applicant(
            ApplicationApplicant.builder()
                .appliedPreviously(true)
                .addresses(List.of(Map.of("k1", "v1"), Map.of("k2", "v2")))
                .relationshipToInvolvedChildren("relationshipToChildren")
                .build())
        .applicationMerits(meritsGenerator.createDefault())
        .proceedings(List.of(proceedingDtoGenerator.createDefault()))
        .submitterEmail("test@example.com")
        .build();
  }
}
