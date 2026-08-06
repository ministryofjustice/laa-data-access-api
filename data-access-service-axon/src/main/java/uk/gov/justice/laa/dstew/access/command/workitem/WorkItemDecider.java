package uk.gov.justice.laa.dstew.access.command.workitem;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Decision functions: derive WorkItem events from current state and command inputs. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class WorkItemDecider {

  /** Returns a {@link WorkItemAssigned}. */
  public static WorkItemAssigned decideAssign(WorkItemState state, AssignWorkItemCommand command) {
    return new WorkItemAssigned(
        command.workItemId(),
        command.caseworkerId(),
        command.serialisedRequest(),
        command.eventDescription(),
        command.occurredAt(),
        command.fanOut());
  }

  /** Returns a {@link WorkItemUnassigned}, or {@code null} when already unassigned. */
  public static WorkItemUnassigned decideUnassign(
      WorkItemState state, UnassignWorkItemCommand command) {
    if (state.caseworkerId == null) {
      return null;
    }
    return new WorkItemUnassigned(
        command.workItemId(),
        command.serialisedRequest(),
        command.eventDescription(),
        command.occurredAt());
  }
}
