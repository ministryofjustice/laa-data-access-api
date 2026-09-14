package uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;

/** Resolves the write-side routing action required to link one application to another. */
@Service
@RequiredArgsConstructor
public class ApplicationGroupRouteResolver {
  private final ApplicationGroupRouteRepository routes;

  /** Locks both routes, then classifies whether linking creates, joins, or reuses a group. */
  @Transactional
  public ApplicationLinkPlan resolve(UUID sourceApplicationId, UUID targetApplicationId) {
    var lockedRoutes =
        routes.findAllByApplicationIdInForUpdate(
            Stream.of(sourceApplicationId, targetApplicationId).sorted().toList());
    var routesByApplicationId =
        lockedRoutes.stream()
            .collect(
                Collectors.toMap(ApplicationGroupRoute::getApplicationId, Function.identity()));
    var sourceRoute = requiredRoute(routesByApplicationId, sourceApplicationId, "source");
    var targetRoute = requiredRoute(routesByApplicationId, targetApplicationId, "target");

    if (isSameLinkedGroup(sourceRoute, targetRoute)) {
      return new ApplicationLinkPlan(
          ApplicationLinkAction.ALREADY_LINKED, sourceRoute.getGroupId());
    }
    if (sourceRoute.getRouteKind() == ApplicationGroupRouteKind.STANDALONE
        && targetRoute.getRouteKind() == ApplicationGroupRouteKind.STANDALONE) {
      return new ApplicationLinkPlan(ApplicationLinkAction.CREATE_GROUP, null);
    }
    if (sourceRoute.getRouteKind() == ApplicationGroupRouteKind.STANDALONE
        && targetRoute.getRouteKind() == ApplicationGroupRouteKind.LINKED_GROUP) {
      return new ApplicationLinkPlan(
          ApplicationLinkAction.ADD_TO_EXISTING_GROUP, targetRoute.getGroupId());
    }
    throw new ApplicationLinkConflictException(
        "Application "
            + sourceRoute.getApplicationId()
            + " already belongs to a different linked group");
  }

  private ApplicationGroupRoute requiredRoute(
      Map<UUID, ApplicationGroupRoute> routesByApplicationId,
      UUID applicationId,
      String routeRole) {
    var route = routesByApplicationId.get(applicationId);
    if (route != null) {
      return route;
    }
    throw new ResourceNotFoundException(
        "No application group route found for " + routeRole + " application " + applicationId);
  }

  private boolean isSameLinkedGroup(
      ApplicationGroupRoute sourceRoute, ApplicationGroupRoute targetRoute) {
    return sourceRoute.getRouteKind() == ApplicationGroupRouteKind.LINKED_GROUP
        && targetRoute.getRouteKind() == ApplicationGroupRouteKind.LINKED_GROUP
        && java.util.Objects.equals(sourceRoute.getGroupId(), targetRoute.getGroupId());
  }
}
