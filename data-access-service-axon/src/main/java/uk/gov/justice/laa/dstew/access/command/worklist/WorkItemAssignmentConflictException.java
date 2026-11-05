package uk.gov.justice.laa.dstew.access.command.worklist;

import java.util.UUID;
import uk.gov.justice.laa.dstew.access.shared.logging.ClientFacingException;

/** Raised when an assignment is stale or incompatible with the item's current assignment state. */
public class WorkItemAssignmentConflictException extends RuntimeException
    implements ClientFacingException {
  public WorkItemAssignmentConflictException(UUID workItemId, String reason) {
    super("Work item " + workItemId + " cannot be updated: " + reason);
  }
}
