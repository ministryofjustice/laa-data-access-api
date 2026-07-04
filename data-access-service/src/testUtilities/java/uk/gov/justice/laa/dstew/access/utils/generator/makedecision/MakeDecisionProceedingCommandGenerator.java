package uk.gov.justice.laa.dstew.access.utils.generator.makedecision;

import java.util.UUID;
import uk.gov.justice.laa.dstew.access.domain.enums.MeritsDecisionStatus;
import uk.gov.justice.laa.dstew.access.usecase.makedecision.MakeDecisionProceedingCommand;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

/** Generator for {@link MakeDecisionProceedingCommand} test instances. */
public class MakeDecisionProceedingCommandGenerator
    extends BaseGenerator<
        MakeDecisionProceedingCommand,
        MakeDecisionProceedingCommand.MakeDecisionProceedingCommandBuilder> {

  /** Constructs the generator. */
  public MakeDecisionProceedingCommandGenerator() {
    super(
        MakeDecisionProceedingCommand::toBuilder,
        MakeDecisionProceedingCommand.MakeDecisionProceedingCommandBuilder::build);
  }

  @Override
  public MakeDecisionProceedingCommand createDefault() {
    return MakeDecisionProceedingCommand.builder()
        .proceedingId(UUID.randomUUID())
        .decision(MeritsDecisionStatus.GRANTED)
        .reason("default reason")
        .justification("default justification")
        .build();
  }
}
