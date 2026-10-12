package uk.gov.justice.laa.dstew.access.utils.generator.application;

import java.time.LocalDate;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.usecase.shared.parser.ApplicationMerits;
import uk.gov.justice.laa.dstew.access.usecase.shared.parser.InvolvedChild;
import uk.gov.justice.laa.dstew.access.usecase.shared.parser.OpponentDetails;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

public class ApplicationMeritsGenerator
    extends BaseGenerator<ApplicationMerits, ApplicationMerits.ApplicationMeritsBuilder> {
  private final OpponentDetailsGenerator opponentDetailsGenerator = new OpponentDetailsGenerator();

  public static final UUID DEFAULT_INVOLVED_CHILD_ID =
      UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
  public static final String DEFAULT_INVOLVED_CHILD_FIRST_NAME = "John";
  public static final String DEFAULT_INVOLVED_CHILD_LAST_NAME = "Smith";
  public static final LocalDate DEFAULT_INVOLVED_CHILD_DATE_OF_BIRTH = LocalDate.of(2022, 8, 20);

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
                InvolvedChild.builder()
                    .id(DEFAULT_INVOLVED_CHILD_ID)
                    .firstName(DEFAULT_INVOLVED_CHILD_FIRST_NAME)
                    .lastName(DEFAULT_INVOLVED_CHILD_LAST_NAME)
                    .dateOfBirth(DEFAULT_INVOLVED_CHILD_DATE_OF_BIRTH)
                    .build()))
        .build();
  }
}
