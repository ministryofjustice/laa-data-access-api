package uk.gov.justice.laa.dstew.access.command.worklist.route;

/**
 * Command authority for a work item: its own aggregate ({@code STANDALONE}) or the linked group
 * that owns its assignment ({@code LINKED_GROUP}).
 */
public enum WorkItemRouteKind {
  STANDALONE,
  LINKED_GROUP
}
