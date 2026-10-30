package uk.gov.justice.laa.dstew.access.command.worklist.unassign;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.command.worklist.ExpectedAssignment;

/** Returns an assigned work item to the open work list. */
public record UnassignWorkItemCommand(
    UUID workItemId,
    ExpectedAssignment expectedAssignment,
    String serialisedRequest,
    String eventDescription,
    Instant occurredAt) {
  /** Validates the identifiers and timestamp required to issue an unassignment. */
  public UnassignWorkItemCommand {
    Objects.requireNonNull(workItemId, "workItemId must not be null");
    Objects.requireNonNull(expectedAssignment, "expectedAssignment must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
  }
}
