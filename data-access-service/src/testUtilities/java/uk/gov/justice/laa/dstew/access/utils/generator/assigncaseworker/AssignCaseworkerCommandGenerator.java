package uk.gov.justice.laa.dstew.access.utils.generator.assigncaseworker;

import java.util.List;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.usecase.assigncaseworker.AssignCaseworkerCommand;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

/** Test data generator for {@link AssignCaseworkerCommand}. */
public class AssignCaseworkerCommandGenerator
    extends BaseGenerator<
        AssignCaseworkerCommand, AssignCaseworkerCommand.AssignCaseworkerCommandBuilder> {

  public AssignCaseworkerCommandGenerator() {
    super(
        AssignCaseworkerCommand::toBuilder,
        AssignCaseworkerCommand.AssignCaseworkerCommandBuilder::build);
  }

  @Override
  public AssignCaseworkerCommand createDefault() {
    return AssignCaseworkerCommand.builder()
        .caseworkerId(UUID.randomUUID())
        .applicationIds(List.of(UUID.randomUUID()))
        .eventDescription("Caseworker assigned.")
        .build();
  }
}
