package uk.gov.justice.laa.dstew.access.command.worklist;

import java.util.UUID;

/** Raised when an assignment is stale or incompatible with the item's current assignment state. */
public class WorkItemAssignmentConflictException extends RuntimeException {
  public WorkItemAssignmentConflictException(UUID workItemId, String reason) {
    super("Work item " + workItemId + " cannot be updated: " + reason);
  }

  private WorkItemAssignmentConflictException(UUID workItemId, String reason, Throwable cause) {
    super("Work item " + workItemId + " cannot be updated: " + reason, cause);
  }

  /** Returns the conflict raised when a concurrent write still failed after its single retry. */
  public static WorkItemAssignmentConflictException concurrentChange(
      UUID workItemId, Throwable cause) {
    return new WorkItemAssignmentConflictException(
        workItemId, "it was changed by a concurrent request; retry the request", cause);
  }
}
