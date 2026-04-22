package uk.gov.justice.laa.dstew.access.utils.generator.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.domain.ApplicationContent;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.proceeding.DomainProceedingGenerator;

/**
 * Generator for {@link ApplicationContent} domain records. Use this in use-case tests where lambdas
 * must type-check against domain builder methods (e.g. {@code ac.proceedings(...)}).
 *
 * <p>For mapper/service/entity tests that need {@code model.ApplicationContent}, use {@link
 * ApplicationContentGenerator} instead.
 */
public class ApplicationContentDomainGenerator
    extends BaseGenerator<ApplicationContent, ApplicationContent.ApplicationContentBuilder> {

  private final DomainProceedingGenerator proceedingGenerator = new DomainProceedingGenerator();

  public ApplicationContentDomainGenerator() {
    super(ApplicationContent::toBuilder, ApplicationContent.ApplicationContentBuilder::build);
  }

  @Override
  public ApplicationContent createDefault() {
    UUID applicationId = UUID.randomUUID();
    return ApplicationContent.builder()
        .id(applicationId)
        .office(Map.of("code", "officeCode"))
        .submittedAt("2024-01-01T12:00:00Z")
        .previousApplicationId("ZZ999Z")
        .lastNameAtBirth("Alberts")
        .correspondenceAddressType("Home")
        .applicant(
            Map.of(
                "appliedPreviously",
                true,
                "addresses",
                List.of(Map.of("k1", "v1"), Map.of("k2", "v2")),
                "relationshipToInvolvedChildren",
                "relationshipToChildren"))
        .proceedings(List.of(proceedingGenerator.createDefault()))
        .submitterEmail("test@example.com")
        .build();
  }
}
