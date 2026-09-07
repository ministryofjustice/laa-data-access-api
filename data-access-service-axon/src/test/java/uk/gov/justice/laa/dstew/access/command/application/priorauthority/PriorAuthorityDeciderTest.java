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
import uk.gov.justice.laa.dstew.access.exception.PriorAuthorityCreationConflictException;
import uk.gov.justice.laa.dstew.access.exception.PriorAuthorityStatusConflictException;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

/** Unit tests for {@link PriorAuthorityDecider}. */
class PriorAuthorityDeciderTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-08-01T10:00:00Z");

  @Test
  void givenEmptyState_whenDecideCreate_thenReturnsEventWithCorrectFields() {
    UUID submissionId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    PriorAuthorityState state = new PriorAuthorityState();
    CreatePriorAuthorityCommand command =
        new CreatePriorAuthorityCommand(
            submissionId,
            applicationId,
            "EXPERT",
            new PriorAuthorityContent(PriorAuthorityType.EXPERT, null, null, null, null),
            "{}",
            1,
            "pa-schema",
            OCCURRED_AT);
    String fingerprint = "test-fingerprint";

    Optional<PriorAuthorityCreatedEvent> result =
        PriorAuthorityDecider.decideCreate(state, command, fingerprint);

    assertThat(result).isPresent();
    PriorAuthorityCreatedEvent event = result.get();
    assertThat(event.submissionId()).isEqualTo(submissionId);
    assertThat(event.applicationId()).isEqualTo(applicationId);
    assertThat(event.priorAuthorityType()).isEqualTo("EXPERT");
    assertThat(event.dataVersion()).isEqualTo(0L);
    assertThat(event.requestFingerprint()).isEqualTo(fingerprint);
    assertThat(event.status()).isEqualTo(PriorAuthorityStatus.PENDING.name());
    assertThat(event.schemaVersion()).isEqualTo(1);
    assertThat(event.occurredAt()).isEqualTo(OCCURRED_AT);
  }

  @Test
  void givenExistingStateWithSameFingerprint_whenDecideCreate_thenReturnsEmpty() {
    UUID submissionId = UUID.randomUUID();
    String fingerprint = "same-fingerprint";
    PriorAuthorityState state = stateAfterCreate(submissionId, fingerprint);
    CreatePriorAuthorityCommand command =
        new CreatePriorAuthorityCommand(
            submissionId, UUID.randomUUID(), null, null, "{}", 1, "pa-schema", OCCURRED_AT);

    Optional<PriorAuthorityCreatedEvent> result =
        PriorAuthorityDecider.decideCreate(state, command, fingerprint);

    assertThat(result).isEmpty();
  }

  @Test
  void givenExistingStateWithDifferentFingerprint_whenDecideCreate_thenThrowsConflictException() {
    UUID submissionId = UUID.randomUUID();
    PriorAuthorityState state = stateAfterCreate(submissionId, "original-fingerprint");
    CreatePriorAuthorityCommand command =
        new CreatePriorAuthorityCommand(
            submissionId,
            UUID.randomUUID(),
            null,
            null,
            "{\"different\":true}",
            1,
            "pa-schema",
            OCCURRED_AT);
    String differentFingerprint = "different-fingerprint";

    assertThatThrownBy(
            () -> PriorAuthorityDecider.decideCreate(state, command, differentFingerprint))
        .isInstanceOf(PriorAuthorityCreationConflictException.class)
        .hasMessage(
            "Prior authority already exists with a different payload for submission: "
                + submissionId)
        .satisfies(
            ex ->
                assertThat(((PriorAuthorityCreationConflictException) ex).getSubmissionId())
                    .isEqualTo(submissionId));
  }

  @Test
  void givenPendingState_whenDecideDecision_thenReturnsDecisionRecordedEvent() {
    UUID submissionId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    PriorAuthorityState state = stateAfterCreate(submissionId, "fingerprint");
    state.applicationId = applicationId;
    state.status = PriorAuthorityStatus.PENDING.name();
    state.dataVersion = 4L;
    MakePriorAuthorityDecisionCommand command =
        new MakePriorAuthorityDecisionCommand(
            submissionId, "GRANTED", "{\"overallDecision\":\"GRANTED\"}", "Recorded", OCCURRED_AT);
    PriorAuthorityDataPayload payload =
        new PriorAuthorityDataPayload(submissionId, applicationId, null, "{}", OCCURRED_AT);

    Optional<PriorAuthorityDecisionRecordedEvent> result =
        PriorAuthorityDecider.decideDecision(state, command, payload);

    assertThat(result).isPresent();
    assertThat(result.get().submissionId()).isEqualTo(submissionId);
    assertThat(result.get().applicationId()).isEqualTo(applicationId);
    assertThat(result.get().dataVersion()).isEqualTo(5L);
    assertThat(result.get().status()).isEqualTo("GRANTED");
  }

  @Test
  void givenDecidedStateWithSameDecisionAndPayload_whenDecideDecision_thenReturnsEmpty() {
    UUID submissionId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    PriorAuthorityState state = stateAfterCreate(submissionId, "fingerprint");
    state.applicationId = applicationId;
    state.status = PriorAuthorityStatus.REFUSED.name();
    MakePriorAuthorityDecisionCommand command =
        new MakePriorAuthorityDecisionCommand(
            submissionId, "REFUSED", "{\"overallDecision\":\"REFUSED\"}", "Recorded", OCCURRED_AT);
    PriorAuthorityDataPayload payload =
        new PriorAuthorityDataPayload(
            submissionId,
            applicationId,
            null,
            "{}",
            OCCURRED_AT,
            "REFUSED",
            "Recorded",
            "{\"overallDecision\":\"REFUSED\"}");

    Optional<PriorAuthorityDecisionRecordedEvent> result =
        PriorAuthorityDecider.decideDecision(state, command, payload);

    assertThat(result).isEmpty();
  }

  @Test
  void givenDecidedStateWithDifferentPayload_whenDecideDecision_thenThrowsConflict() {
    UUID submissionId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    PriorAuthorityState state = stateAfterCreate(submissionId, "fingerprint");
    state.applicationId = applicationId;
    state.status = PriorAuthorityStatus.GRANTED.name();
    MakePriorAuthorityDecisionCommand command =
        new MakePriorAuthorityDecisionCommand(
            submissionId, "REFUSED", "{\"overallDecision\":\"REFUSED\"}", "Recorded", OCCURRED_AT);
    PriorAuthorityDataPayload payload =
        new PriorAuthorityDataPayload(
            submissionId,
            applicationId,
            null,
            "{}",
            OCCURRED_AT,
            "GRANTED",
            "Recorded",
            "{\"overallDecision\":\"GRANTED\"}");

    assertThatThrownBy(() -> PriorAuthorityDecider.decideDecision(state, command, payload))
        .isInstanceOf(PriorAuthorityStatusConflictException.class);
  }

  @Test
  void givenUnsupportedDecisionValue_whenDecideDecision_thenThrowsValidationException() {
    UUID submissionId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    PriorAuthorityState state = stateAfterCreate(submissionId, "fingerprint");
    state.applicationId = applicationId;
    state.status = PriorAuthorityStatus.PENDING.name();
    MakePriorAuthorityDecisionCommand command =
        new MakePriorAuthorityDecisionCommand(
            submissionId, "PART_GRANTED", "{}", "Recorded", OCCURRED_AT);
    PriorAuthorityDataPayload payload =
        new PriorAuthorityDataPayload(submissionId, applicationId, null, "{}", OCCURRED_AT);

    assertThatThrownBy(() -> PriorAuthorityDecider.decideDecision(state, command, payload))
        .isInstanceOf(ValidationException.class)
        .satisfies(
            exception ->
                assertThat(((ValidationException) exception).errors())
                    .containsExactly("overallDecision must be one of: GRANTED, REFUSED"));
  }

  // ── helpers ────────────────────────────────────────────────────────────────────

  private static PriorAuthorityState stateAfterCreate(UUID submissionId, String fingerprint) {
    PriorAuthorityState state = new PriorAuthorityState();
    state.submissionId = submissionId;
    state.requestFingerprint = fingerprint;
    state.dataVersion = 0L;
    state.status = PriorAuthorityStatus.PENDING.name();
    state.schemaVersion = 1;
    return state;
  }
}
