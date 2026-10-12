package uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Represents the assignment state of a work item. */
public record WorkItemAssignment(@Nullable UUID caseworkerId, long assignmentVersion) {

  /** Returns true if the work item is assigned to a caseworker. */
  public boolean isAssigned() {
    return caseworkerId != null;
  }

  /** Returns true if this assignment is identical to another (same caseworker and version). */
  public boolean isSameCaseworkerAs(@Nullable WorkItemAssignment other) {
    if (other == null) {
      return caseworkerId == null;
    }
    return caseworkerId != null && caseworkerId.equals(other.caseworkerId());
  }
}
