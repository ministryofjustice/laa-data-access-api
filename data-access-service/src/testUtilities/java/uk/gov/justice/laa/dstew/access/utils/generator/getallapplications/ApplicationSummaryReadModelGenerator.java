package uk.gov.justice.laa.dstew.access.utils.generator.getallapplications;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.usecase.getallapplications.model.ApplicationSummaryReadModel;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

/** Generator for {@link ApplicationSummaryReadModel} test data. */
public class ApplicationSummaryReadModelGenerator
    extends BaseGenerator<
        ApplicationSummaryReadModel,
        ApplicationSummaryReadModel.ApplicationSummaryReadModelBuilder> {

  /** Constructs the generator. */
  public ApplicationSummaryReadModelGenerator() {
    super(
        ApplicationSummaryReadModel::toBuilder,
        ApplicationSummaryReadModel.ApplicationSummaryReadModelBuilder::build);
  }

  @Override
  public ApplicationSummaryReadModel createDefault() {
    return ApplicationSummaryReadModel.builder()
        .id(UUID.randomUUID())
        .submittedAt(Instant.now())
        .isAutoGranted(false)
        .categoryOfLaw("FAMILY")
        .matterType("SPECIAL_CHILDREN_ACT")
        .usedDelegatedFunctions(false)
        .laaReference("REF7327")
        .officeCode("1A234B")
        .status("APPLICATION_IN_PROGRESS")
        .caseworkerId(UUID.randomUUID())
        .clientFirstName("Jane")
        .clientLastName("Doe")
        .clientDateOfBirth(LocalDate.of(1990, 1, 1))
        .applicationType("INITIAL")
        .modifiedAt(Instant.now())
        .isLead(true)
        .linkedApplications(List.of())
        .build();
  }
}
