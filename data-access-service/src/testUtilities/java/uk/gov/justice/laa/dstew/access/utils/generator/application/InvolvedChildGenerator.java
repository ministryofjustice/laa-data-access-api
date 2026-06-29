package uk.gov.justice.laa.dstew.access.utils.generator.application;

import uk.gov.justice.laa.dstew.access.usecase.shared.parser.InvolvedChild;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

/** Generator for {@link InvolvedChild} test instances. */
public class InvolvedChildGenerator
    extends BaseGenerator<InvolvedChild, InvolvedChild.InvolvedChildBuilder> {

  public InvolvedChildGenerator() {
    super(InvolvedChild::toBuilder, InvolvedChild.InvolvedChildBuilder::build);
  }

  @Override
  public InvolvedChild createDefault() {
    return InvolvedChild.builder()
        .id(ApplicationMeritsGenerator.DEFAULT_INVOLVED_CHILD_ID)
        .fullName(ApplicationMeritsGenerator.DEFAULT_INVOLVED_CHILD_FULL_NAME)
        .dateOfBirth(ApplicationMeritsGenerator.DEFAULT_INVOLVED_CHILD_DATE_OF_BIRTH)
        .build();
  }
}
