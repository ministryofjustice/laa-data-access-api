package uk.gov.justice.laa.dstew.access.command.worklist;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Returns an assigned work item to the open work list. */
public record UnassignWorkItemCommand(
    UUID workItemId,
    long expectedAssignmentVersion,
    String serialisedRequest,
    String eventDescription,
    Instant occurredAt) {
  public UnassignWorkItemCommand {
    Objects.requireNonNull(workItemId, "workItemId must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
  }
}
