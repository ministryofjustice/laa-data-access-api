package uk.gov.justice.laa.dstew.access.command.worklist.route;

/** Command authority for a work item; pending and transitioning routes are intentionally blocked. */
public enum WorkItemRouteKind {
  STANDALONE,
  PENDING_LINKED_GROUP,
  LINKED_GROUP,
  TRANSITIONING
}
