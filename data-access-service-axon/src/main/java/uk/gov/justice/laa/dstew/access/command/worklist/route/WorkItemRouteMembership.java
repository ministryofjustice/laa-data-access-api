package uk.gov.justice.laa.dstew.access.command.worklist.route;

import java.util.UUID;
import org.jspecify.annotations.Nullable;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;

/** Read-only route fields used to choose which rows to lock, without attaching the entity. */
public interface WorkItemRouteMembership {
  WorkItemType getWorkItemType();

  WorkItemRouteKind getRouteKind();

  @Nullable UUID getGroupId();
}
