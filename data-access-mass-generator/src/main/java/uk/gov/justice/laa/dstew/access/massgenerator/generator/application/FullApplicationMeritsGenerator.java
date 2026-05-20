package uk.gov.justice.laa.dstew.access.massgenerator.generator.application;

import java.util.List;
import uk.gov.justice.laa.dstew.access.model.ApplicationMerits;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

public class FullApplicationMeritsGenerator
    extends BaseGenerator<ApplicationMerits, ApplicationMerits.ApplicationMeritsBuilder> {

  private final FullOpponentDetailsGenerator opponentDetailsGenerator =
      new FullOpponentDetailsGenerator();

  public FullApplicationMeritsGenerator() {
    super(ApplicationMerits::toBuilder, ApplicationMerits.ApplicationMeritsBuilder::build);
  }

  @Override
  public ApplicationMerits createDefault() {
    return ApplicationMerits.builder()
        .involvedChildren(List.of())
        .build()
        .putAdditionalContent("opponents", List.of(opponentDetailsGenerator.createDefault()))
        .putAdditionalContent("statementOfCase", null)
        .putAdditionalContent("domesticAbuseSummary", null)
        .putAdditionalContent("partiesMentalCapacity", null)
        .putAdditionalContent("latestIncident", null)
        .putAdditionalContent("allegation", null)
        .putAdditionalContent("undertaking", null)
        .putAdditionalContent("urgency", null)
        .putAdditionalContent("appeal", null)
        .putAdditionalContent("matterOpposition", null);
  }
}
