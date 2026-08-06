package uk.gov.justice.laa.dstew.access.command.workitem;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Event-fold functions for {@link WorkItemState}. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class WorkItemEvolve {

  /** Applies a {@link WorkItemAssigned} to the given state. */
  public static void apply(WorkItemState state, WorkItemAssigned event) {
    state.workItemId = event.workItemId();
    state.caseworkerId = event.caseworkerId();
  }

  /** Applies a {@link WorkItemUnassigned} to the given state. */
  public static void apply(WorkItemState state, WorkItemUnassigned event) {
    state.workItemId = event.workItemId();
    state.caseworkerId = null;
  }
}
