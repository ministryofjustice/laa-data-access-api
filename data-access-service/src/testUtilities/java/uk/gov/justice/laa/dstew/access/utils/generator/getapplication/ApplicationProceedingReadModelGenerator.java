package uk.gov.justice.laa.dstew.access.utils.generator.getapplication;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.usecase.getapplication.model.ApplicationProceedingReadModel;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

/** Generator for {@link ApplicationProceedingReadModel} test data. */
public class ApplicationProceedingReadModelGenerator
    extends BaseGenerator<
        ApplicationProceedingReadModel,
        ApplicationProceedingReadModel.ApplicationProceedingReadModelBuilder> {

  private final InvolvedChildReadModelGenerator involvedChildReadModelGenerator =
      new InvolvedChildReadModelGenerator();
  private final ScopeLimitationReadModelGenerator scopeLimitationReadModelGenerator =
      new ScopeLimitationReadModelGenerator();

  /** Constructs the generator. */
  public ApplicationProceedingReadModelGenerator() {
    super(
        ApplicationProceedingReadModel::toBuilder,
        ApplicationProceedingReadModel.ApplicationProceedingReadModelBuilder::build);
  }

  @Override
  public ApplicationProceedingReadModel createDefault() {
    return ApplicationProceedingReadModel.builder()
        .proceedingId(UUID.randomUUID())
        .description("Test proceeding")
        .proceedingType("hearing")
        .categoryOfLaw("FAMILY")
        .matterType("SPECIAL_CHILDREN_ACT")
        .levelOfService("Full representation")
        .substantiveCostLimitation(1350.0)
        .delegatedFunctionsDate(LocalDate.of(2025, 5, 6))
        .meritsDecision("REFUSED")
        .involvedChildren(List.of(involvedChildReadModelGenerator.createDefault()))
        .scopeLimitations(List.of(scopeLimitationReadModelGenerator.createDefault()))
        .build();
  }
}
