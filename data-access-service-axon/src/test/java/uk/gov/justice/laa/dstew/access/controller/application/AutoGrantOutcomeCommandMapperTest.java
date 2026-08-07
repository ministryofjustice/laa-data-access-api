package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import uk.gov.justice.laa.dstew.access.command.application.decision.MakeApplicationDecisionCommand;
import uk.gov.justice.laa.dstew.access.command.application.ready.MarkApplicationReadyCommand;
import uk.gov.justice.laa.dstew.access.model.AutoGrantOutcome;
import uk.gov.justice.laa.dstew.access.model.AutograntedOutcomeRequest;
import uk.gov.justice.laa.dstew.access.model.EventHistoryRequest;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionProceedingRequest;
import uk.gov.justice.laa.dstew.access.model.ManualOutcomeRequest;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionDetailsRequest;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionStatus;

class AutoGrantOutcomeCommandMapperTest {

  private final AutoGrantOutcomeCommandMapper mapper =
      new AutoGrantOutcomeCommandMapper(JsonMapper.builder().build());

  @Test
  void mapsManualOutcomeWithoutDecisionContent() {
    UUID id = UUID.randomUUID();
    var request = new ManualOutcomeRequest(AutoGrantOutcome.MANUAL, 4L);

    var command = (MarkApplicationReadyCommand) mapper.toCommand(id, request);

    assertThat(command.applicationId()).isEqualTo(id);
    assertThat(command.expectedApplicationVersion()).isEqualTo(4L);
  }

  @Test
  void mapsAutomaticGrantOutcomeToCompleteGrantedDecision() {
    UUID id = UUID.randomUUID();
    UUID proceedingId = UUID.randomUUID();
    var request =
        new AutograntedOutcomeRequest(
            AutoGrantOutcome.AUTOGRANTED,
            7L,
            AutograntedOutcomeRequest.OverallDecisionEnum.GRANTED,
            List.of(
                new MakeDecisionProceedingRequest(
                    proceedingId,
                    new MeritsDecisionDetailsRequest(MeritsDecisionStatus.GRANTED, "Autogranted"))),
            new EventHistoryRequest().eventDescription("Automatic assessment passed"),
            Map.of("certificateNumber", "AUTO-2126"));

    var command = (MakeApplicationDecisionCommand) mapper.toCommand(id, request);

    assertThat(command.applicationId()).isEqualTo(id);
    assertThat(command.expectedApplicationVersion()).isEqualTo(7L);
    assertThat(command.overallDecision()).isEqualTo("GRANTED");
    assertThat(command.autoGranted()).isTrue();
    assertThat(command.fromAutoGrantOutcome()).isTrue();
    assertThat(command.proceedings())
        .singleElement()
        .extracting("proceedingId")
        .isEqualTo(proceedingId);
    assertThat(command.certificate()).containsEntry("certificateNumber", "AUTO-2126");
  }
}
