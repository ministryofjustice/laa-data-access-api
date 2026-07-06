package uk.gov.justice.laa.dstew.access.utils.generator.getallapplications;

import java.time.LocalDate;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.usecase.getallapplications.GetAllApplicationsCommand;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

/** Generates {@link GetAllApplicationsCommand} instances for use in tests. */
public class GetAllApplicationsCommandGenerator
    extends BaseGenerator<
        GetAllApplicationsCommand, GetAllApplicationsCommand.GetAllApplicationsCommandBuilder> {

  /** Constructs the generator. */
  public GetAllApplicationsCommandGenerator() {
    super(
        GetAllApplicationsCommand::toBuilder,
        GetAllApplicationsCommand.GetAllApplicationsCommandBuilder::build);
  }

  @Override
  public GetAllApplicationsCommand createDefault() {
    return GetAllApplicationsCommand.builder()
        .status("APPLICATION_SUBMITTED")
        .laaReference("REF7327")
        .clientFirstName("Jane")
        .clientLastName("Doe")
        .clientDateOfBirth(LocalDate.of(1990, 1, 1))
        .userId(UUID.randomUUID())
        .isAutoGranted(false)
        .matterType("SPECIAL_CHILDREN_ACT")
        .sortBy("SUBMITTED_DATE")
        .orderBy("ASC")
        .page(1)
        .pageSize(10)
        .build();
  }
}
