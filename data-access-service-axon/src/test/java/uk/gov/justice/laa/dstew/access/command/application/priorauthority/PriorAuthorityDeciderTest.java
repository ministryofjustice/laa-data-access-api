package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityStatus;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityType;
import uk.gov.justice.laa.dstew.access.exception.PriorAuthorityStatusConflictException;
import uk.gov.justice.laa.dstew.access.exception.PriorAuthorityVersionConflictException;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

/** Unit tests for {@link PriorAuthorityDecider}. */
class PriorAuthorityDeciderTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-08-01T10:00:00Z");

  @Test
  void givenCommand_whenDecideStartDraft_thenReturnsEventWithExpectedFields() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    CreatePriorAuthorityDraftCommand command =
        new CreatePriorAuthorityDraftCommand(
            priorAuthorityId,
            applicationId,
            new PriorAuthorityContent(PriorAuthorityType.EXPERT, null, null, null, null),
            "{}",
            1,
            "PriorAuthority.json",
            OCCURRED_AT);
    PriorAuthorityDraftStartedEvent event = PriorAuthorityDecider.decideStartDraft(command);

    assertThat(event.priorAuthorityId()).isEqualTo(priorAuthorityId);
    assertThat(event.applicationId()).isEqualTo(applicationId);
    assertThat(event.priorAuthorityType()).isEqualTo(PriorAuthorityType.EXPERT.name());
    assertThat(event.schemaVersion()).isEqualTo(1);
    assertThat(event.occurredAt()).isEqualTo(OCCURRED_AT);
  }

  @Test
  void givenSubmitCommand_whenDecideSubmit_thenAlwaysUsesDataVersionZero() {
    UUID priorAuthorityId = UUID.randomUUID();
    PriorAuthorityState state = new PriorAuthorityState();
    state.applicationId = UUID.randomUUID();
    state.priorAuthorityType = PriorAuthorityType.COUNSEL.name();
    state.schemaVersion = 2;
    SubmitPriorAuthorityDraftCommand command =
        new SubmitPriorAuthorityDraftCommand(priorAuthorityId, OCCURRED_AT);

    PriorAuthoritySubmittedEvent event = PriorAuthorityDecider.decideSubmit(command, state);

    assertThat(event.priorAuthorityId()).isEqualTo(priorAuthorityId);
    assertThat(event.applicationId()).isEqualTo(state.applicationId);
    assertThat(event.priorAuthorityType()).isEqualTo(PriorAuthorityType.COUNSEL.name());
    assertThat(event.schemaVersion()).isEqualTo(2);
    assertThat(event.dataVersion()).isEqualTo(0L);
    assertThat(event.occurredAt()).isEqualTo(OCCURRED_AT);
  }

  @Test
  void givenSubmittedState_whenDecideDecision_thenReturnsDecisionRecordedEvent() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    PriorAuthorityState state = submittedState(priorAuthorityId, applicationId, 4L);
    MakePriorAuthorityDecisionCommand command =
        new MakePriorAuthorityDecisionCommand(
            priorAuthorityId,
            4L,
            "GRANTED",
            "Recorded",
            350.0,
            null,
            OCCURRED_AT,
            "{\"decision\":\"GRANTED\"}",
            OCCURRED_AT);

    Optional<PriorAuthorityDecisionRecordedEvent> result =
        PriorAuthorityDecider.decideDecision(
            state, command, payload(priorAuthorityId, applicationId));

    assertThat(result).isPresent();
    assertThat(result.get().submissionId()).isEqualTo(priorAuthorityId);
    assertThat(result.get().applicationId()).isEqualTo(applicationId);
    assertThat(result.get().priorAuthorityType()).isEqualTo(PriorAuthorityType.EXPERT.name());
    assertThat(result.get().dataVersion()).isEqualTo(5L);
    assertThat(result.get().status()).isEqualTo("GRANTED");
  }

  @Test
  void givenAlreadyDecidedWithSameDecisionAndPayload_whenDecideDecision_thenReturnsEmpty() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    PriorAuthorityState state = submittedState(priorAuthorityId, applicationId, 1L);
    state.status = "REFUSED";
    MakePriorAuthorityDecisionCommand command =
        new MakePriorAuthorityDecisionCommand(
            priorAuthorityId,
            1L,
            "REFUSED",
            "Recorded",
            0.0,
            null,
            OCCURRED_AT,
            "{\"decision\":\"REFUSED\"}",
            OCCURRED_AT);
    PriorAuthorityDataPayload payload =
        new PriorAuthorityDataPayload(
            priorAuthorityId,
            applicationId,
            new PriorAuthorityContent(PriorAuthorityType.EXPERT, null, null, null, null),
            "{}",
            OCCURRED_AT,
            "REFUSED",
            "Recorded",
            0.0,
            OCCURRED_AT,
            "{\"decision\":\"REFUSED\"}");

    Optional<PriorAuthorityDecisionRecordedEvent> result =
        PriorAuthorityDecider.decideDecision(state, command, payload);

    assertThat(result).isEmpty();
  }

  @Test
  void givenUnsupportedDecisionValue_whenDecideDecision_thenThrowsValidationException() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    PriorAuthorityState state = submittedState(priorAuthorityId, applicationId, 0L);
    MakePriorAuthorityDecisionCommand command =
        new MakePriorAuthorityDecisionCommand(
            priorAuthorityId,
            0L,
            "PART_GRANTED",
            "Recorded",
            0.0,
            null,
            OCCURRED_AT,
            "{}",
            OCCURRED_AT);

    assertThatThrownBy(
            () ->
                PriorAuthorityDecider.decideDecision(
                    state, command, payload(priorAuthorityId, applicationId)))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void givenSubmittedStateWithDifferentVersion_whenDecideDecision_thenThrowsVersionConflict() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    PriorAuthorityState state = submittedState(priorAuthorityId, applicationId, 3L);
    MakePriorAuthorityDecisionCommand command =
        new MakePriorAuthorityDecisionCommand(
            priorAuthorityId,
            2L,
            "GRANTED",
            "Recorded",
            100.0,
            null,
            OCCURRED_AT,
            "{\"decision\":\"GRANTED\"}",
            OCCURRED_AT);

    assertThatThrownBy(
            () ->
                PriorAuthorityDecider.decideDecision(
                    state, command, payload(priorAuthorityId, applicationId)))
        .isInstanceOf(PriorAuthorityVersionConflictException.class);
  }

  @Test
  void givenAlreadyDecidedWithDifferentPayload_whenDecideDecision_thenThrowsConflict() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    PriorAuthorityState state = submittedState(priorAuthorityId, applicationId, 1L);
    state.status = "GRANTED";
    MakePriorAuthorityDecisionCommand command =
        new MakePriorAuthorityDecisionCommand(
            priorAuthorityId,
            1L,
            "REFUSED",
            "Recorded",
            0.0,
            null,
            OCCURRED_AT,
            "{\"decision\":\"REFUSED\"}",
            OCCURRED_AT);

    assertThatThrownBy(
            () ->
                PriorAuthorityDecider.decideDecision(
                    state, command, payload(priorAuthorityId, applicationId)))
        .isInstanceOf(PriorAuthorityStatusConflictException.class);
  }

  private static PriorAuthorityState submittedState(
      UUID priorAuthorityId, UUID applicationId, long dataVersion) {
    PriorAuthorityState state = new PriorAuthorityState();
    state.priorAuthorityId = priorAuthorityId;
    state.applicationId = applicationId;
    state.priorAuthorityType = PriorAuthorityType.EXPERT.name();
    state.schemaVersion = 1;
    state.dataVersion = dataVersion;
    state.status = PriorAuthorityStatus.SUBMITTED.name();
    return state;
  }

  private static PriorAuthorityDataPayload payload(UUID priorAuthorityId, UUID applicationId) {
    return new PriorAuthorityDataPayload(
        priorAuthorityId,
        applicationId,
        new PriorAuthorityContent(PriorAuthorityType.EXPERT, null, null, null, null),
        "{}",
        OCCURRED_AT,
        "GRANTED",
        "Recorded",
        0.0,
        OCCURRED_AT,
        "{\"decision\":\"GRANTED\"}");
  }
}
