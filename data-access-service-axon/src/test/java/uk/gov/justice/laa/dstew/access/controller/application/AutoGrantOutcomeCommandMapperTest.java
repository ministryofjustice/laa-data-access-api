package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import uk.gov.justice.laa.dstew.access.command.application.decision.RecordAutoGrantedOutcomeCommand;
import uk.gov.justice.laa.dstew.access.command.application.ready.MarkApplicationReadyCommand;
import uk.gov.justice.laa.dstew.access.model.AutoGrantOutcome;
import uk.gov.justice.laa.dstew.access.model.AutoGrantedOutcomeRequest;
import uk.gov.justice.laa.dstew.access.model.ManualOutcomeRequest;

class AutoGrantOutcomeCommandMapperTest {

  private final AutoGrantOutcomeCommandMapper mapper =
      new AutoGrantOutcomeCommandMapper(JsonMapper.builder().build());

  @Test
  void mapsManualOutcomeWithoutDecisionContent() {
    UUID id = UUID.randomUUID();
    var request = new ManualOutcomeRequest(AutoGrantOutcome.MANUAL);

    var command = (MarkApplicationReadyCommand) mapper.toCommand(id, request);

    assertThat(command.applicationId()).isEqualTo(id);
    assertThat(command.serialisedRequest()).contains("MANUAL");
  }

  @Test
  void mapsAutomaticGrantOutcomeWithoutCallerOwnedDecisionData() {
    UUID id = UUID.randomUUID();
    var request =
        new AutoGrantedOutcomeRequest(
            AutoGrantOutcome.AUTOGRANTED, Map.of("certificateNumber", "AUTO-2126"));

    var command = (RecordAutoGrantedOutcomeCommand) mapper.toCommand(id, request);

    assertThat(command.applicationId()).isEqualTo(id);
    assertThat(command.certificate()).containsEntry("certificateNumber", "AUTO-2126");
  }
}
