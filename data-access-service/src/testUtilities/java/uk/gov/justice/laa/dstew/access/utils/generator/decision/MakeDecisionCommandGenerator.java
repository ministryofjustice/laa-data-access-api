package uk.gov.justice.laa.dstew.access.utils.generator.decision;

import java.util.List;
import java.util.UUID;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.domain.MeritsDecisionOutcome;
import uk.gov.justice.laa.dstew.access.domain.OverallDecisionStatus;
import uk.gov.justice.laa.dstew.access.usecase.makedecision.MakeDecisionCommand;
import uk.gov.justice.laa.dstew.access.usecase.makedecision.MakeDecisionProceedingCommand;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

public class MakeDecisionCommandGenerator
    extends BaseGenerator<MakeDecisionCommand, MakeDecisionCommand.MakeDecisionCommandBuilder> {

  public MakeDecisionCommandGenerator() {
    super(MakeDecisionCommand::toBuilder, MakeDecisionCommand.MakeDecisionCommandBuilder::build);
  }

  @Override
  public MakeDecisionCommand createDefault() {
    UUID proceedingId = UUID.randomUUID();
    MakeDecisionProceedingCommand proceeding =
        MakeDecisionProceedingCommand.builder()
            .proceedingId(proceedingId)
            .meritsDecision(MeritsDecisionOutcome.REFUSED)
            .reason("Test reason")
            .justification("Test justification")
            .build();
    MakeDecisionCommand cmd =
        MakeDecisionCommand.builder()
            .applicationId(UUID.randomUUID())
            .applicationVersion(0L)
            .autoGranted(false)
            .overallDecision(OverallDecisionStatus.REFUSED)
            .proceedings(List.of(proceeding))
            .certificate(null)
            .eventDescription("Test event description")
            .build();
    return cmd.toBuilder().serialisedRequest(serialise(cmd)).build();
  }

  private String serialise(Object obj) {
    try {
      return new ObjectMapper().writeValueAsString(obj);
    } catch (tools.jackson.core.JacksonException e) {
      throw new IllegalStateException("Failed to serialise", e);
    }
  }
}
