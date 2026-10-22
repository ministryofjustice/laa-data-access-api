package uk.gov.justice.laa.dstew.access.command.worklist;

import java.util.Objects;
import java.util.UUID;

/** The assignment owner and version an assign or unassign request was based on. */
public record ExpectedAssignment(UUID ownerId, long version) {
  /** Validates an expected assignment token's owner and version. */
  public ExpectedAssignment {
    Objects.requireNonNull(ownerId, "ownerId must not be null");
    if (version < 0) {
      throw new IllegalArgumentException("version must not be negative");
    }
  }

  /** Requires the token to name the current assignment owner. */
  public void requireOwner(UUID currentOwnerId, UUID workItemId) {
    if (!ownerId.equals(currentOwnerId)) {
      throw new WorkItemAssignmentConflictException(workItemId, "the assignment version is stale");
    }
  }
}
