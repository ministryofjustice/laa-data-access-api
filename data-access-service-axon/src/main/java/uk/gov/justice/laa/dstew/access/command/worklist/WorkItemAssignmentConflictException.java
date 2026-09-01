package uk.gov.justice.laa.dstew.access.command.worklist;

/** Raised when an assignment is stale or incompatible with the item's current assignment state. */
public class WorkItemAssignmentConflictException extends RuntimeException {
  public WorkItemAssignmentConflictException(WorkItemId workItemId, String reason) {
    super(
        "Work item " + workItemId.type() + " " + workItemId.id() + " cannot be updated: " + reason);
  }
}
