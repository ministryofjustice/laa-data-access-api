package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemUnassigned;

/** Unit tests for {@link PriorAuthorityEvolve}. */
class PriorAuthorityEvolveTest {

  @Test
  void givenDraftStartedEvent_whenApply_thenMutatesStateFields() {
    PriorAuthorityState state = new PriorAuthorityState();
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-08-01T10:00:00Z");
    PriorAuthorityDraftStartedEvent event =
        new PriorAuthorityDraftStartedEvent(
            priorAuthorityId, applicationId, "EXPERT", 3, occurredAt);

    PriorAuthorityEvolve.apply(state, event);

    assertThat(state.getPriorAuthorityId()).isEqualTo(priorAuthorityId);
    assertThat(state.getApplicationId()).isEqualTo(applicationId);
    assertThat(state.getPriorAuthorityType()).isEqualTo("EXPERT");
    assertThat(state.getSchemaVersion()).isEqualTo(3);
  }

  @Test
  void givenSubmittedEvent_whenApply_thenMutatesStateFields() {
    PriorAuthorityState state = new PriorAuthorityState();
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-08-01T10:00:00Z");
    PriorAuthoritySubmittedEvent event =
        new PriorAuthoritySubmittedEvent(
            priorAuthorityId, applicationId, "EXPERT", 3, 0L, occurredAt);

    PriorAuthorityEvolve.apply(state, event);

    assertThat(state.getPriorAuthorityId()).isEqualTo(priorAuthorityId);
    assertThat(state.getApplicationId()).isEqualTo(applicationId);
    assertThat(state.getPriorAuthorityType()).isEqualTo("EXPERT");
    assertThat(state.getSchemaVersion()).isEqualTo(3);
    assertThat(state.getDataVersion()).isEqualTo(0L);
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
            workItemId, WorkItemType.PRIOR_AUTHORITY, 3L, 4L, caseworkerId, occurredAt));

    assertThat(state.getAssignmentVersion()).isEqualTo(4L);
    assertThat(state.getCaseworkerId()).isEqualTo(caseworkerId);

    PriorAuthorityEvolve.apply(
        state,
        new WorkItemUnassigned(workItemId, WorkItemType.PRIOR_AUTHORITY, 3L, 5L, occurredAt));

    assertThat(state.getAssignmentVersion()).isEqualTo(5L);
    assertThat(state.getCaseworkerId()).isNull();
  }

  @Test
  void givenDocumentUploadedEvent_whenApply_thenTracksDocumentId() {
    PriorAuthorityState state = new PriorAuthorityState();
    UUID documentId = UUID.randomUUID();

    PriorAuthorityEvolve.apply(
        state,
        new PriorAuthorityDocumentUploadedEvent(
            UUID.randomUUID(), documentId, Instant.now(), 10L, "application/pdf", "sum"));

    assertThat(state.getUploadedDocumentIds()).contains(documentId);
  }
}
