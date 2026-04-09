package uk.gov.justice.laa.dstew.access.utils.generator.application;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import uk.gov.justice.laa.dstew.access.model.ApplicationMerits;
import uk.gov.justice.laa.dstew.access.model.OpponentDetails;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

public class ApplicationMeritsGenerator
    extends BaseGenerator<ApplicationMerits, ApplicationMerits.ApplicationMeritsBuilder> {
  private final OpponentDetailsGenerator opponentDetailsGenerator = new OpponentDetailsGenerator();

  public ApplicationMeritsGenerator() {
    super(ApplicationMerits::toBuilder, ApplicationMerits.ApplicationMeritsBuilder::build);
  }

  @Override
  public ApplicationMerits createDefault() {
    return ApplicationMerits.builder()
        .opponents(
            new LinkedList<OpponentDetails>(List.of(opponentDetailsGenerator.createDefault())))
        .involvedChildren(
            List.of(
                Map.of(
                    "first_name",
                    "John",
                    "last_name",
                    "Smith",
                    "date_of_birth",
                    "Mon Aug 20 2022 20:20:00 GMT+0100 (British Summer Time)")))
        .build();
  }
}
