package uk.gov.justice.laa.dstew.access.utils.generator.getapplication;

import uk.gov.justice.laa.dstew.access.usecase.getapplication.model.OpponentReadModel;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

/** Generator for {@link OpponentReadModel} test data. */
public class OpponentReadModelGenerator
    extends BaseGenerator<OpponentReadModel, OpponentReadModel.OpponentReadModelBuilder> {

  /** Constructs the generator. */
  public OpponentReadModelGenerator() {
    super(OpponentReadModel::toBuilder, OpponentReadModel.OpponentReadModelBuilder::build);
  }

  @Override
  public OpponentReadModel createDefault() {
    return OpponentReadModel.builder()
        .opponentType("ApplicationMeritsTask::Individual")
        .firstName("John")
        .lastName("Smith")
        .organisationName("Acme Ltd")
        .build();
  }
}
