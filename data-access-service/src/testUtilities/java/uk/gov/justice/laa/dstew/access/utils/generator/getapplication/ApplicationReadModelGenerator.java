package uk.gov.justice.laa.dstew.access.utils.generator.getapplication;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.domain.ApplicationReadModel;
import uk.gov.justice.laa.dstew.access.domain.ProviderReadModel;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

/** Generator for {@link ApplicationReadModel} test data. */
public class ApplicationReadModelGenerator
    extends BaseGenerator<ApplicationReadModel, ApplicationReadModel.ApplicationReadModelBuilder> {

  private final ApplicationProceedingReadModelGenerator applicationProceedingReadModelGenerator =
      new ApplicationProceedingReadModelGenerator();
  private final OpponentReadModelGenerator opponentReadModelGenerator =
      new OpponentReadModelGenerator();

  /** Constructs the generator. */
  public ApplicationReadModelGenerator() {
    super(ApplicationReadModel::toBuilder, ApplicationReadModel.ApplicationReadModelBuilder::build);
  }

  @Override
  public ApplicationReadModel createDefault() {
    return ApplicationReadModel.builder()
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
        .applicationType("INITIAL")
        .version(0L)
        .opponents(List.of(opponentReadModelGenerator.createDefault()))
        .provider(
            ProviderReadModel.builder()
                .officeCode("officeCode")
                .contactEmail("test@example.com")
                .build())
        .proceedings(List.of(applicationProceedingReadModelGenerator.createDefault()))
        .build();
  }
}
