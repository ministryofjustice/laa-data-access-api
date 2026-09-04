package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemUnassigned;
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
  void givenGenericAssignmentEvents_whenApply_thenUpdatesAndClearsTheAssignmentState() {
    PriorAuthorityState state = new PriorAuthorityState();
    UUID workItemId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-08-01T10:00:00Z");

    PriorAuthorityEvolve.apply(
        state,
        new WorkItemAssigned(
            workItemId,
            WorkItemType.PRIOR_AUTHORITY,
            3L,
            4L,
            caseworkerId,
            "Assigned",
            occurredAt));

    assertThat(state.getAssignmentVersion()).isEqualTo(4L);
    assertThat(state.getCaseworkerId()).isEqualTo(caseworkerId);

    PriorAuthorityEvolve.apply(
        state,
        new WorkItemUnassigned(
            workItemId, WorkItemType.PRIOR_AUTHORITY, 3L, 5L, "Unassigned", occurredAt));

    assertThat(state.getAssignmentVersion()).isEqualTo(5L);
    assertThat(state.getCaseworkerId()).isNull();
  }
}
