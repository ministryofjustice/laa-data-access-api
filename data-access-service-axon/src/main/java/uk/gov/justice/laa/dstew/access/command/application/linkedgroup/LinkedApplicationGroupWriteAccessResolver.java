package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationGroupRoute;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationGroupRouteRepository;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationLinkConflictException;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.security.ProtectedResource;
import uk.gov.justice.laa.dstew.access.security.WriteAccessTargetResolver;

/** Resolves locked route ownership for explicit application linking. */
@Component
public class LinkedApplicationGroupWriteAccessResolver implements WriteAccessTargetResolver {
  private final ApplicationGroupRouteRepository routes;

  public LinkedApplicationGroupWriteAccessResolver(ApplicationGroupRouteRepository routes) {
    this.routes = routes;
  }

  @Override
  public List<ProtectedResource> resolve(Object resource) {
    if (resource instanceof LinkApplicationCommand linkCommand) {
      return resolveLink(linkCommand);
    }
    if (resource instanceof UnlinkApplicationCommand unlinkCommand) {
      return resolveGroupMutation(unlinkCommand.applicationId());
    }
    if (resource instanceof MakeApplicationLeadCommand makeLeadCommand) {
      return resolveGroupMutation(makeLeadCommand.applicationId());
    }
    throw new IllegalStateException(
        "Unsupported linked-application resource " + resource.getClass());
  }

  private List<ProtectedResource> resolveLink(LinkApplicationCommand command) {
    var routesByApplicationId =
        routes
            .findLinkRoutesForUpdate(
                Stream.of(command.sourceApplicationId(), command.targetApplicationId())
                    .sorted()
                    .toList(),
                command.targetApplicationId())
            .stream()
            .collect(
                Collectors.toMap(
                    ApplicationGroupRoute::getApplicationId,
                    route -> route,
                    (first, ignored) -> first,
                    LinkedHashMap::new));
    var source = requiredRoute(routesByApplicationId, command.sourceApplicationId(), "source");
    var target = requiredRoute(routesByApplicationId, command.targetApplicationId(), "target");

    var targetGroupRoutes =
        target.getGroupId() == null
            ? List.of(target)
            : routesByApplicationId.values().stream()
                .filter(route -> target.getGroupId().equals(route.getGroupId()))
                .toList();
    return Stream.concat(Stream.of(source), targetGroupRoutes.stream())
        .distinct()
        .map(this::toProtectedResource)
        .toList();
  }

  private List<ProtectedResource> resolveGroupMutation(UUID applicationId) {
    var membership =
        routes
            .findMembershipByApplicationId(applicationId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "No application group route found for application " + applicationId));
    UUID groupId = membership.getGroupId();
    if (groupId == null) {
      throw new ApplicationLinkConflictException(
          "Application " + applicationId + " is not in a linked group");
    }
    var lockedRoutes = routes.findAllByGroupIdForUpdate(groupId);
    var applicationRoute =
        lockedRoutes.stream()
            .filter(route -> route.getApplicationId().equals(applicationId))
            .findFirst()
            .orElseThrow(
                () ->
                    new ApplicationLinkConflictException(
                        "Application " + applicationId + " is not in a linked group"));
    return List.of(toProtectedResource(applicationRoute));
  }

  private ProtectedResource toProtectedResource(ApplicationGroupRoute route) {
    return new ProtectedResource(route.getOfficeCode());
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
}
