package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.MakePriorAuthorityDecisionCommand;
import uk.gov.justice.laa.dstew.access.model.DecisionStatus;
import uk.gov.justice.laa.dstew.access.model.EventHistoryRequest;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionProceedingRequest;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionRequest;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionDetailsRequest;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionStatus;

class MakePriorAuthorityDecisionCommandMapperTest {

  private final MakePriorAuthorityDecisionCommandMapper mapper =
      new MakePriorAuthorityDecisionCommandMapper(JsonMapper.builder().build());

  @Test
  void givenRequestWithEventDescription_whenMapped_thenUsesEventDescriptionAsJustification() {
    UUID submissionId = UUID.randomUUID();
    MakeDecisionRequest request =
        MakeDecisionRequest.builder()
            .applicationVersion(7L)
            .overallDecision(DecisionStatus.GRANTED)
            .proceedings(List.of(proceeding("ignored justification")))
            .eventHistory(
                EventHistoryRequest.builder().eventDescription("Decision recorded").build())
            .build();

    MakePriorAuthorityDecisionCommand command = mapper.toCommand(submissionId, request);

    assertThat(command.submissionId()).isEqualTo(submissionId);
    assertThat(command.overallDecision()).isEqualTo("GRANTED");
    assertThat(command.decisionJustification()).isEqualTo("Decision recorded");
    assertThat(command.serialisedRequest()).contains("\"overallDecision\":\"GRANTED\"");
    assertThat(command.occurredAt()).isNotNull();
  }

  @Test
  void givenMissingEventDescription_whenMapped_thenFallsBackToProceedingJustification() {
    MakeDecisionRequest request =
        MakeDecisionRequest.builder()
            .applicationVersion(8L)
            .overallDecision(DecisionStatus.REFUSED)
            .proceedings(List.of(proceeding("Refusal reason")))
            .eventHistory(EventHistoryRequest.builder().eventDescription("   ").build())
            .build();

    MakePriorAuthorityDecisionCommand command = mapper.toCommand(UUID.randomUUID(), request);

    assertThat(command.overallDecision()).isEqualTo("REFUSED");
    assertThat(command.decisionJustification()).isEqualTo("Refusal reason");
  }

  @Test
  void givenSerializationFailure_whenMapped_thenWrapsInIllegalStateException() throws Exception {
    ObjectMapper objectMapper = mock(ObjectMapper.class);
    MakePriorAuthorityDecisionCommandMapper failingMapper =
        new MakePriorAuthorityDecisionCommandMapper(objectMapper);
    MakeDecisionRequest request =
        MakeDecisionRequest.builder()
            .applicationVersion(9L)
            .overallDecision(DecisionStatus.GRANTED)
            .proceedings(List.of(proceeding("Decision recorded")))
            .eventHistory(
                EventHistoryRequest.builder().eventDescription("Decision recorded").build())
            .build();
    when(objectMapper.writeValueAsString(request)).thenThrow(new JacksonException("boom") {});

    assertThatThrownBy(() -> failingMapper.toCommand(UUID.randomUUID(), request))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Unable to serialise MakeDecisionRequest")
        .hasCauseInstanceOf(JacksonException.class);
  }

  private MakeDecisionProceedingRequest proceeding(String justification) {
    return MakeDecisionProceedingRequest.builder()
        .proceedingId(UUID.randomUUID())
        .meritsDecision(
            MeritsDecisionDetailsRequest.builder()
                .decision(MeritsDecisionStatus.REFUSED)
                .reason("reason")
                .justification(justification)
                .build())
        .build();
  }
}
