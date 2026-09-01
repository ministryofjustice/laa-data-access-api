package uk.gov.justice.laa.dstew.access.command.worklist;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Assigns an open work item through its authoritative assignment boundary. */
public record AssignWorkItemCommand(
    WorkItemId workItemId,
    UUID caseworkerId,
    long expectedAssignmentVersion,
    String serialisedRequest,
    String eventDescription,
    Instant occurredAt) {
  /** Validates the stable identifiers required to issue an assignment. */
  public AssignWorkItemCommand {
    Objects.requireNonNull(workItemId, "workItemId must not be null");
    Objects.requireNonNull(caseworkerId, "caseworkerId must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
  }
}
