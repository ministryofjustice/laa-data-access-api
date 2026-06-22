package uk.gov.justice.laa.dstew.access.utils.generator.getapplication;

import uk.gov.justice.laa.dstew.access.domain.ScopeLimitationReadModel;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

/** Generator for {@link ScopeLimitationReadModel} test data. */
public class ScopeLimitationReadModelGenerator
    extends BaseGenerator<
        ScopeLimitationReadModel, ScopeLimitationReadModel.ScopeLimitationReadModelBuilder> {

  /** Constructs the generator. */
  public ScopeLimitationReadModelGenerator() {
    super(
        ScopeLimitationReadModel::toBuilder,
        ScopeLimitationReadModel.ScopeLimitationReadModelBuilder::build);
  }

  @Override
  public ScopeLimitationReadModel createDefault() {
    return ScopeLimitationReadModel.builder()
        .scopeLimitation("CV027")
        .scopeDescription("Limitation of costs applies")
        .build();
  }
}
