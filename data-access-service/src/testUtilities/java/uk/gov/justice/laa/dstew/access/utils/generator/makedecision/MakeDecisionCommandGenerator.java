package uk.gov.justice.laa.dstew.access.utils.generator.makedecision;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.usecase.makedecision.MakeDecisionCommand;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

/** Generator for {@link MakeDecisionCommand} test instances. */
public class MakeDecisionCommandGenerator
    extends BaseGenerator<MakeDecisionCommand, MakeDecisionCommand.MakeDecisionCommandBuilder> {

  private final MakeDecisionProceedingCommandGenerator proceedingCommandGenerator =
      new MakeDecisionProceedingCommandGenerator();

  /** Constructs the generator. */
  public MakeDecisionCommandGenerator() {
    super(MakeDecisionCommand::toBuilder, MakeDecisionCommand.MakeDecisionCommandBuilder::build);
  }

  @Override
  public MakeDecisionCommand createDefault() {
    return MakeDecisionCommand.builder()
        .applicationId(UUID.randomUUID())
        .applicationVersion(0L)
        .overallDecision("GRANTED")
        .autoGranted(false)
        .proceedings(List.of(proceedingCommandGenerator.createDefault()))
        .certificate(Map.of("certificateKey", "certificateValue"))
        .serialisedRequest("{\"overallDecision\":\"GRANTED\"}")
        .eventDescription("test event description")
        .build();
  }
}
