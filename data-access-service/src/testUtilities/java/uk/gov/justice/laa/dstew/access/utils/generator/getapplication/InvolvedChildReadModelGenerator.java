package uk.gov.justice.laa.dstew.access.utils.generator.getapplication;

import java.time.LocalDate;
import uk.gov.justice.laa.dstew.access.usecase.getapplication.model.InvolvedChildReadModel;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

/** Generator for {@link InvolvedChildReadModel} test data. */
public class InvolvedChildReadModelGenerator
    extends BaseGenerator<
        InvolvedChildReadModel, InvolvedChildReadModel.InvolvedChildReadModelBuilder> {

  /** Constructs the generator. */
  public InvolvedChildReadModelGenerator() {
    super(
        InvolvedChildReadModel::toBuilder,
        InvolvedChildReadModel.InvolvedChildReadModelBuilder::build);
  }

  @Override
  public InvolvedChildReadModel createDefault() {
    return InvolvedChildReadModel.builder()
        .fullName("John Smith")
        .dateOfBirth(LocalDate.of(2022, 8, 20))
        .build();
  }
}
