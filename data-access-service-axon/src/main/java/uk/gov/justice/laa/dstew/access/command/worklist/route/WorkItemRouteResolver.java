package uk.gov.justice.laa.dstew.access.command.worklist.route;

import java.util.UUID;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;

/** Resolves work items that can be commanded through their direct aggregate route. */
@Service
public class WorkItemRouteResolver {
  private final WorkItemRouteRepository routes;

  public WorkItemRouteResolver(WorkItemRouteRepository routes) {
    this.routes = routes;
  }

  /** Returns the direct authoritative route for the supplied work-item identifier. */
  public WorkItemRoute resolveDirectRoute(UUID workItemId) {
    WorkItemRoute route =
        routes
            .findByWorkItemId(workItemId)
            .orElseThrow(
                () -> new ResourceNotFoundException("No work-item route found for " + workItemId));
    if (route.getRouteKind() != WorkItemRouteKind.STANDALONE) {
      throw new ResourceNotFoundException("No direct work-item route found for " + workItemId);
    }
    return route;
  }
}
