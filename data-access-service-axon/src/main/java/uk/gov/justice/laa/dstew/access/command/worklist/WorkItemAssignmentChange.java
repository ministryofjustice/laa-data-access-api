package uk.gov.justice.laa.dstew.access.command.worklist;

import java.util.UUID;

/** A change to a work item's assignment: {@link WorkItemAssigned} or {@link WorkItemUnassigned}. */
public sealed interface WorkItemAssignmentChange permits WorkItemAssigned, WorkItemUnassigned {

  /** Returns the ID of the work item whose assignment changed. */
  UUID workItemId();
}
