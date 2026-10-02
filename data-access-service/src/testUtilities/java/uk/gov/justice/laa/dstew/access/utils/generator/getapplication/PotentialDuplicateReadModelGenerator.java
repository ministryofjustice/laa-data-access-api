package uk.gov.justice.laa.dstew.access.utils.generator.getapplication;

import java.util.UUID;
import uk.gov.justice.laa.dstew.access.usecase.getapplication.model.PotentialDuplicateReadModel;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

/** Generator for {@link PotentialDuplicateReadModel} test data. */
public class PotentialDuplicateReadModelGenerator
    extends BaseGenerator<
        PotentialDuplicateReadModel,
        PotentialDuplicateReadModel.PotentialDuplicateReadModelBuilder> {

  /** Constructs the generator. */
  public PotentialDuplicateReadModelGenerator() {
    super(
        PotentialDuplicateReadModel::toBuilder,
        PotentialDuplicateReadModel.PotentialDuplicateReadModelBuilder::build);
  }

  @Override
  public PotentialDuplicateReadModel createDefault() {
    return PotentialDuplicateReadModel.builder()
        .applicationId(UUID.randomUUID())
        .laaReference("DUP7328")
        .legacyReference("LEGACY123")
        .build();
  }
}
