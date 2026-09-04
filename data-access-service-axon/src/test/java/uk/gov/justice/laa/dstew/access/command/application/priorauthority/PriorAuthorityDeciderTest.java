package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityStatus;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityType;
import uk.gov.justice.laa.dstew.access.exception.PriorAuthorityCreationConflictException;

/** Unit tests for {@link PriorAuthorityDecider}. */
class PriorAuthorityDeciderTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-08-01T10:00:00Z");

  @Test
  void givenEmptyState_whenDecideCreate_thenReturnsEventWithCorrectFields() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    PriorAuthorityState state = new PriorAuthorityState();
    CreatePriorAuthorityCommand command =
        new CreatePriorAuthorityCommand(
            priorAuthorityId,
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
    assertThat(event.priorAuthorityId()).isEqualTo(priorAuthorityId);
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
    UUID priorAuthorityId = UUID.randomUUID();
    String fingerprint = "same-fingerprint";
    PriorAuthorityState state = stateAfterCreate(priorAuthorityId, fingerprint);
    CreatePriorAuthorityCommand command =
        new CreatePriorAuthorityCommand(
            priorAuthorityId, UUID.randomUUID(), null, null, "{}", 1, "pa-schema", OCCURRED_AT);

    Optional<PriorAuthorityCreatedEvent> result =
        PriorAuthorityDecider.decideCreate(state, command, fingerprint);

    assertThat(result).isEmpty();
  }

  @Test
  void givenExistingStateWithDifferentFingerprint_whenDecideCreate_thenThrowsConflictException() {
    UUID priorAuthorityId = UUID.randomUUID();
    PriorAuthorityState state = stateAfterCreate(priorAuthorityId, "original-fingerprint");
    CreatePriorAuthorityCommand command =
        new CreatePriorAuthorityCommand(
            priorAuthorityId,
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
                + priorAuthorityId)
        .satisfies(
            ex ->
                assertThat(((PriorAuthorityCreationConflictException) ex).getPriorAuthorityId())
                    .isEqualTo(priorAuthorityId));
  }

  // ── helpers ────────────────────────────────────────────────────────────────────

  private static PriorAuthorityState stateAfterCreate(UUID priorAuthorityId, String fingerprint) {
    PriorAuthorityState state = new PriorAuthorityState();
    state.priorAuthorityId = priorAuthorityId;
    state.requestFingerprint = fingerprint;
    state.dataVersion = 0L;
    state.status = PriorAuthorityStatus.PENDING.name();
    state.schemaVersion = 1;
    return state;
  }

  @Test
  void givenCommand_whenDecideStartDraft_thenReturnsEventWithExpectedFields() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    CreatePriorAuthorityDraftCommand command =
        new CreatePriorAuthorityDraftCommand(
            priorAuthorityId,
            applicationId,
            new PriorAuthorityContent(null, null, null, null, null),
            "{}",
            1,
            "PriorAuthority.json",
            OCCURRED_AT);
    String fingerprint = "draft-fingerprint";

    PriorAuthorityDraftStartedEvent event =
        PriorAuthorityDecider.decideStartDraft(command, fingerprint, applicationId);

    assertThat(event.priorAuthorityId()).isEqualTo(priorAuthorityId);
    assertThat(event.applicationId()).isEqualTo(applicationId);
    assertThat(event.requestFingerprint()).isEqualTo(fingerprint);
    assertThat(event.schemaVersion()).isEqualTo(1);
    assertThat(event.occurredAt()).isEqualTo(OCCURRED_AT);
  }

  @Test
  void givenSubmitCommand_whenDecideSubmit_thenAlwaysUsesDataVersionZero() {
    UUID priorAuthorityId = UUID.randomUUID();
    PriorAuthorityState state = new PriorAuthorityState();
    state.applicationId = UUID.randomUUID();
    SubmitPriorAuthorityDraftCommand command =
        new SubmitPriorAuthorityDraftCommand(priorAuthorityId, OCCURRED_AT);

    PriorAuthoritySubmittedEvent event = PriorAuthorityDecider.decideSubmit(command, state);

    assertThat(event.priorAuthorityId()).isEqualTo(priorAuthorityId);
    assertThat(event.applicationId()).isEqualTo(state.applicationId);
    assertThat(event.dataVersion()).isEqualTo(0L);
    assertThat(event.status()).isEqualTo(PriorAuthorityStatus.PENDING.name());
    assertThat(event.occurredAt()).isEqualTo(OCCURRED_AT);
  }
}
