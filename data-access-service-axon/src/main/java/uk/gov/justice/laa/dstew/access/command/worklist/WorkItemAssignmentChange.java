package uk.gov.justice.laa.dstew.access.command.worklist;

/** A change to a work item's assignment: {@link WorkItemAssigned} or {@link WorkItemUnassigned}. */
public sealed interface WorkItemAssignmentChange permits WorkItemAssigned, WorkItemUnassigned {}
