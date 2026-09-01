package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityStatus;

/** Unit tests for {@link PriorAuthorityEvolve}. */
class PriorAuthorityEvolveTest {

  @Test
  void givenCreatedEvent_whenApply_thenMutatesAllSixStateFields() {
    PriorAuthorityState state = new PriorAuthorityState();
    UUID submissionId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-08-01T10:00:00Z");
    PriorAuthorityCreatedEvent event =
        new PriorAuthorityCreatedEvent(
            submissionId,
            applicationId,
            0L,
            "test-fingerprint",
            PriorAuthorityStatus.PENDING.name(),
            2,
            occurredAt);

    PriorAuthorityEvolve.apply(state, event);

    assertThat(state.getSubmissionId()).isEqualTo(submissionId);
    assertThat(state.getApplicationId()).isEqualTo(applicationId);
    assertThat(state.getDataVersion()).isEqualTo(0L);
    assertThat(state.getRequestFingerprint()).isEqualTo("test-fingerprint");
    assertThat(state.getStatus()).isEqualTo(PriorAuthorityStatus.PENDING.name());
    assertThat(state.getSchemaVersion()).isEqualTo(2);
  }

  @Test
  void givenDraftSavedEvent_whenApply_thenMutatesAllSixStateFields() {
    PriorAuthorityState state = new PriorAuthorityState();
    UUID submissionId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-08-01T10:00:00Z");
    PriorAuthorityDraftSavedEvent event =
        new PriorAuthorityDraftSavedEvent(
            submissionId,
            applicationId,
            1L,
            "draft-fingerprint",
            PriorAuthorityStatus.IN_PROGRESS.name(),
            3,
            occurredAt);

    PriorAuthorityEvolve.apply(state, event);

    assertThat(state.getSubmissionId()).isEqualTo(submissionId);
    assertThat(state.getApplicationId()).isEqualTo(applicationId);
    assertThat(state.getDataVersion()).isEqualTo(1L);
    assertThat(state.getRequestFingerprint()).isEqualTo("draft-fingerprint");
    assertThat(state.getStatus()).isEqualTo(PriorAuthorityStatus.IN_PROGRESS.name());
    assertThat(state.getSchemaVersion()).isEqualTo(3);
  }
}
