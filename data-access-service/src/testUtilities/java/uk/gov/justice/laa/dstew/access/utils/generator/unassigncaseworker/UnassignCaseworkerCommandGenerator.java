package uk.gov.justice.laa.dstew.access.utils.generator.unassigncaseworker;

import java.util.UUID;
import uk.gov.justice.laa.dstew.access.usecase.unassigncaseworker.UnassignCaseworkerCommand;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

/** Test data generator for {@link UnassignCaseworkerCommand}. */
public class UnassignCaseworkerCommandGenerator
    extends BaseGenerator<
        UnassignCaseworkerCommand, UnassignCaseworkerCommand.UnassignCaseworkerCommandBuilder> {

  /** Constructs the generator. */
  public UnassignCaseworkerCommandGenerator() {
    super(
        UnassignCaseworkerCommand::toBuilder,
        UnassignCaseworkerCommand.UnassignCaseworkerCommandBuilder::build);
  }

  @Override
  public UnassignCaseworkerCommand createDefault() {
    return UnassignCaseworkerCommand.builder()
        .applicationId(UUID.randomUUID())
        .eventDescription("Caseworker unassigned.")
        .build();
  }
}
