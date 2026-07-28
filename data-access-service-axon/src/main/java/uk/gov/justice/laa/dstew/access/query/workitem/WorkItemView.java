package uk.gov.justice.laa.dstew.access.query.workitem;

import java.time.Instant;
import java.util.UUID;

/** Immutable view of a single work item returned to caseworker clients. */
public record WorkItemView(
    UUID workItemId,
    WorkType workType,
    UUID applicationId,
    UUID priorAuthorityId,
    String laaReference,
    UUID assignedCaseworkerId,
    Instant createdAt,
    Instant updatedAt) {

  /** Maps a persisted row to its client-facing view. */
  public static WorkItemView from(WorkItemRecord record) {
    return new WorkItemView(
        record.getWorkItemId(),
        record.getWorkType(),
        record.getApplicationId(),
        record.getPriorAuthorityId(),
        record.getLaaReference(),
        record.getAssignedCaseworkerId(),
        record.getCreatedAt(),
        record.getUpdatedAt());
  }
}
