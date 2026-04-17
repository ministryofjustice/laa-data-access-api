package uk.gov.justice.laa.dstew.access.massgenerator.generator.application;

import java.util.List;
import uk.gov.justice.laa.dstew.access.massgenerator.model.FullProceedingMerits;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

public class FullProceedingMeritsGenerator
    extends BaseGenerator<FullProceedingMerits, FullProceedingMerits.FullProceedingMeritsBuilder> {

  public FullProceedingMeritsGenerator() {
    super(FullProceedingMerits::toBuilder, FullProceedingMerits.FullProceedingMeritsBuilder::build);
  }

  @Override
  public FullProceedingMerits createDefault() {
    return FullProceedingMerits.builder()
        .opponentsApplication(null)
        .attemptsToSettle(null)
        .specificIssue(null)
        .varyOrder(null)
        .chancesOfSuccess(null)
        .prohibitedSteps(null)
        .childCareAssessment(null)
        .proceedingLinkedChildren(List.of())
        .involvedChildren(List.of())
        .build();
  }
}
