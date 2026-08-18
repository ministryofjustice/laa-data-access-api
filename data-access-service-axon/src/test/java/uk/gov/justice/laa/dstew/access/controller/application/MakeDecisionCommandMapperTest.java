package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import uk.gov.justice.laa.dstew.access.model.DecisionStatus;
import uk.gov.justice.laa.dstew.access.model.EventHistoryRequest;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionProceedingRequest;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionRequest;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionDetailsRequest;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionStatus;

class MakeDecisionCommandMapperTest {

  private final MakeDecisionCommandMapper mapper =
      new MakeDecisionCommandMapper(JsonMapper.builder().build());

  @Test
  void givenCompleteRequest_whenMapped_thenMapsCommandAndAuditData() {
    UUID applicationId = UUID.randomUUID();
    UUID proceedingId = UUID.randomUUID();
    MakeDecisionRequest request =
        MakeDecisionRequest.builder()
            .applicationVersion(3L)
            .overallDecision(DecisionStatus.REFUSED)
            .autoGranted(false)
            .proceedings(
                List.of(
                    MakeDecisionProceedingRequest.builder()
                        .proceedingId(proceedingId)
                        .meritsDecision(
                            MeritsDecisionDetailsRequest.builder()
                                .decision(MeritsDecisionStatus.REFUSED)
                                .reason("insufficient evidence")
                                .justification("Evidence threshold not met")
                                .build())
                        .build()))
            .certificate(Map.of("reference", "CERT-1"))
            .eventHistory(
                EventHistoryRequest.builder().eventDescription("Decision recorded").build())
            .build();
    Instant before = Instant.now();

    var command = mapper.toCommand(applicationId, request);

    assertThat(command.applicationId()).isEqualTo(applicationId);
    assertThat(command.expectedApplicationVersion()).isEqualTo(3L);
    assertThat(command.overallDecision()).isEqualTo("REFUSED");
    assertThat(command.autoGranted()).isFalse();
    assertThat(command.certificate()).containsEntry("reference", "CERT-1");
    assertThat(command.eventDescription()).isEqualTo("Decision recorded");
    assertThat(command.occurredAt()).isBetween(before, Instant.now());
    assertThat(command.serialisedRequest())
        .contains(proceedingId.toString(), "Evidence threshold not met", "Decision recorded");
    assertThat(command.proceedings())
        .singleElement()
        .satisfies(
            proceeding -> {
              assertThat(proceeding.proceedingId()).isEqualTo(proceedingId);
              assertThat(proceeding.decision()).isEqualTo("REFUSED");
              assertThat(proceeding.reason()).isEqualTo("insufficient evidence");
              assertThat(proceeding.justification()).isEqualTo("Evidence threshold not met");
            });
  }

  @Test
  void givenNullProceedingsAndHistory_whenMapped_thenUsesEmptyProceedingsAndNullDescription() {
    UUID applicationId = UUID.randomUUID();
    MakeDecisionRequest request =
        MakeDecisionRequest.builder()
            .applicationVersion(0L)
            .overallDecision(DecisionStatus.REFUSED)
            .autoGranted(false)
            .proceedings(null)
            .build();

    var command = mapper.toCommand(applicationId, request);

    assertThat(command.proceedings()).isEmpty();
    assertThat(command.eventDescription()).isNull();
  }

  @Test
  void givenProceedingWithoutMeritsDecision_whenMapped_thenPreservesNullDecisionDetails() {
    MakeDecisionRequest request =
        MakeDecisionRequest.builder()
            .applicationVersion(0L)
            .overallDecision(DecisionStatus.REFUSED)
            .autoGranted(false)
            .proceedings(
                List.of(
                    MakeDecisionProceedingRequest.builder()
                        .proceedingId(UUID.randomUUID())
                        .build()))
            .build();

    var proceeding = mapper.toCommand(UUID.randomUUID(), request).proceedings().getFirst();

    assertThat(proceeding.decision()).isNull();
    assertThat(proceeding.reason()).isNull();
    assertThat(proceeding.justification()).isNull();
  }
}
