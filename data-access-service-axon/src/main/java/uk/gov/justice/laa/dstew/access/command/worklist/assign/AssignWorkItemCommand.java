package uk.gov.justice.laa.dstew.access.command.worklist.assign;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.command.worklist.ExpectedAssignment;

/** Assigns an open work item through its authoritative assignment boundary. */
public record AssignWorkItemCommand(
    UUID workItemId,
    UUID caseworkerId,
    ExpectedAssignment expectedAssignment,
    String serialisedRequest,
    String eventDescription,
    Instant occurredAt) {
  /** Validates the stable identifiers required to issue an assignment. */
  public AssignWorkItemCommand {
    Objects.requireNonNull(workItemId, "workItemId must not be null");
    Objects.requireNonNull(caseworkerId, "caseworkerId must not be null");
    Objects.requireNonNull(expectedAssignment, "expectedAssignment must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
  }
}
