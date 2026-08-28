package uk.gov.justice.laa.dstew.access.command.worklist;

import java.util.Objects;
import java.util.UUID;

/** Public, aggregate-independent identity of an assignable work item. */
public record WorkItemId(WorkItemType type, UUID id) {
  public WorkItemId {
    Objects.requireNonNull(type, "type must not be null");
    Objects.requireNonNull(id, "id must not be null");
  }
}
