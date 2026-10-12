package uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Read-only membership fields used to select the group routes to lock. */
public interface ApplicationGroupRouteMembership {
  ApplicationGroupRouteKind getRouteKind();

  @Nullable UUID getGroupId();
}
