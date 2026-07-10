package uk.gov.justice.laa.dstew.access.utils.generator.getallapplications;

import java.util.UUID;
import uk.gov.justice.laa.dstew.access.usecase.getallapplications.model.LinkedApplicationSummaryReadModel;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

/** Generator for {@link LinkedApplicationSummaryReadModel} test data. */
public class LinkedApplicationSummaryReadModelGenerator
    extends BaseGenerator<
        LinkedApplicationSummaryReadModel,
        LinkedApplicationSummaryReadModel.LinkedApplicationSummaryReadModelBuilder> {

  /** Constructs the generator. */
  public LinkedApplicationSummaryReadModelGenerator() {
    super(
        LinkedApplicationSummaryReadModel::toBuilder,
        LinkedApplicationSummaryReadModel.LinkedApplicationSummaryReadModelBuilder::build);
  }

  @Override
  public LinkedApplicationSummaryReadModel createDefault() {
    return LinkedApplicationSummaryReadModel.builder()
        .applicationId(UUID.randomUUID())
        .laaReference("REF7327")
        .isLead(false)
        .leadApplicationId(UUID.randomUUID())
        .build();
  }
}
