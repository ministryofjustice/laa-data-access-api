package uk.gov.justice.laa.dstew.access.utils.generator.getapplication;

import java.time.Instant;
import java.util.Collections;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.usecase.getapplication.dto.ApplicationDbProjection;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

/** Generator for {@link ApplicationDbProjection} test data. */
public class ApplicationDbProjectionGenerator
    extends BaseGenerator<
        ApplicationDbProjection, ApplicationDbProjection.ApplicationDbProjectionBuilder> {

  private final ProceedingDbProjectionGenerator proceedingDbProjectionGenerator =
      new ProceedingDbProjectionGenerator();

  /** Constructs the generator. */
  public ApplicationDbProjectionGenerator() {
    super(
        ApplicationDbProjection::toBuilder,
        ApplicationDbProjection.ApplicationDbProjectionBuilder::build);
  }

  @Override
  public ApplicationDbProjection createDefault() {
    return ApplicationDbProjection.builder()
        .id(UUID.randomUUID())
        .status("APPLICATION_IN_PROGRESS")
        .laaReference("REF7327")
        .updatedAt(Instant.now())
        .caseworkerId(UUID.randomUUID())
        .submittedAt(Instant.parse("2024-01-01T12:00:00Z"))
        .isLead(false)
        .usedDelegatedFunctions(false)
        .autoGrant(true)
        .decisionStatus("GRANTED")
        .version(0L)
        .officeCode("officeCode")
        .submitterEmail("test@example.com")
        .opponents(Collections.emptyList())
        .proceedings(Collections.singletonList(proceedingDbProjectionGenerator.createDefault()))
        .build();
  }
}
