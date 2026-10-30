package uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.access.exception.LinkedApplicationGroupVersionConflictException;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

/** Resolves the write-side routing action required to link one application to another. */
@Service
@RequiredArgsConstructor
public class ApplicationGroupRouteResolver {
  private final ApplicationGroupRouteRepository routes;

  /** Locks both routes, then classifies whether linking creates, joins, or reuses a group. */
  @Transactional
  public ApplicationLinkPlan resolve(UUID sourceApplicationId, UUID targetApplicationId) {
    var targetMembership = routes.findMembershipByApplicationId(targetApplicationId);
    var applicationIds = new TreeSet<>(List.of(sourceApplicationId, targetApplicationId));
    targetMembership
        .filter(membership -> membership.getRouteKind() == ApplicationGroupRouteKind.LINKED_GROUP)
        .map(ApplicationGroupRouteMembership::getGroupId)
        .ifPresent(groupId -> applicationIds.addAll(routes.findApplicationIdsByGroupId(groupId)));
    var lockedRoutes = routes.findAllByApplicationIdInForUpdate(List.copyOf(applicationIds));
    var routesByApplicationId =
        lockedRoutes.stream()
            .collect(
                Collectors.toMap(ApplicationGroupRoute::getApplicationId, Function.identity()));
    var sourceRoute = requiredRoute(routesByApplicationId, sourceApplicationId, "source");
    var targetRoute = requiredRoute(routesByApplicationId, targetApplicationId, "target");

    if (targetRoute.getRouteKind() == ApplicationGroupRouteKind.LINKED_GROUP) {
      var groupId = targetRoute.getGroupId();
      if (targetMembership.isEmpty()
          || targetMembership.get().getRouteKind() != ApplicationGroupRouteKind.LINKED_GROUP
          || !Objects.equals(targetMembership.get().getGroupId(), groupId)
          || routes.findApplicationIdsByGroupId(groupId).stream()
              .anyMatch(applicationId -> !applicationIds.contains(applicationId))) {
        throw membershipChanged(targetApplicationId);
      }
    } else if (targetMembership
        .filter(membership -> membership.getRouteKind() == ApplicationGroupRouteKind.LINKED_GROUP)
        .isPresent()) {
      throw membershipChanged(targetApplicationId);
    }

    if (!sourceRoute.hasSameOfficeCodeAs(targetRoute)) {
      throw new ValidationException(List.of("Applications must have the same office code"));
    }

    ApplicationLinkPlan plan;
    if (isSameLinkedGroup(sourceRoute, targetRoute)) {
      plan =
          new ApplicationLinkPlan(ApplicationLinkAction.ALREADY_LINKED, sourceRoute.getGroupId());
    } else if (sourceRoute.getRouteKind() == ApplicationGroupRouteKind.STANDALONE
        && targetRoute.getRouteKind() == ApplicationGroupRouteKind.STANDALONE) {
      plan = new ApplicationLinkPlan(ApplicationLinkAction.CREATE_GROUP, null);
    } else if (sourceRoute.getRouteKind() == ApplicationGroupRouteKind.STANDALONE
        && targetRoute.getRouteKind() == ApplicationGroupRouteKind.LINKED_GROUP) {
      plan =
          new ApplicationLinkPlan(
              ApplicationLinkAction.ADD_TO_EXISTING_GROUP, targetRoute.getGroupId());
    } else {
      throw new ApplicationLinkConflictException(
          "Application "
              + sourceRoute.getApplicationId()
              + " already belongs to a different linked group");
    }
    return plan;
  }

  /**
   * Locks the routes that an assignment or readiness change of {@code applicationId} depends on and
   * returns its linked group, or empty when it is standalone or has no route.
   */
  @Transactional
  public Optional<UUID> lockForAssignment(UUID applicationId) {
    var membership = routes.findMembershipByApplicationId(applicationId);
    if (membership.isEmpty()) {
      return Optional.empty();
    }
    if (membership.get().getRouteKind() == ApplicationGroupRouteKind.STANDALONE) {
      var lockedRoute = routes.findByApplicationIdForUpdate(applicationId);
      if (lockedRoute.isEmpty()
          || lockedRoute.get().getRouteKind() != ApplicationGroupRouteKind.STANDALONE) {
        throw membershipChanged(applicationId);
      }
      return Optional.empty();
    }

    var groupId = membership.get().getGroupId();
    var applicationIds = routes.findApplicationIdsByGroupId(groupId);
    if (!applicationIds.contains(applicationId)) {
      throw membershipChanged(applicationId);
    }
    var lockedRoutes = routes.findAllByApplicationIdInForUpdate(applicationIds);
    if (lockedRoutes.size() != applicationIds.size()
        || lockedRoutes.stream()
            .anyMatch(
                route ->
                    route.getRouteKind() != ApplicationGroupRouteKind.LINKED_GROUP
                        || !Objects.equals(route.getGroupId(), groupId))
        || !routes.findApplicationIdsByGroupId(groupId).equals(applicationIds)) {
      throw membershipChanged(applicationId);
    }
    return Optional.of(groupId);
  }

  /**
   * Locks every route in the application's linked group, verifies it is the group the caller last
   * read, and returns its identifier.
   */
  @Transactional
  public UUID resolveGroupForMutation(UUID applicationId, UUID expectedGroupId) {
    var membership =
        routes
            .findMembershipByApplicationId(applicationId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "No application group route found for application " + applicationId));
    if (membership.getRouteKind() == ApplicationGroupRouteKind.STANDALONE) {
      throw notInGroup(applicationId);
    }
    var groupId = membership.getGroupId();
    var lockedRoutes = routes.findAllByGroupIdForUpdate(groupId);
    var stillMember =
        lockedRoutes.stream().anyMatch(route -> route.getApplicationId().equals(applicationId));
    if (!stillMember) {
      throw notInGroup(applicationId);
    }
    if (!Objects.equals(groupId, expectedGroupId)) {
      throw LinkedApplicationGroupVersionConflictException.groupChanged(applicationId);
    }
    return groupId;
  }

  /**
   * Locks the application's current route and, when linked, all routes in its group before Prior
   * Authority creation. The caller retains these locks for its surrounding transaction.
   */
  @Transactional
  public Optional<UUID> resolveGroupForPriorAuthorityCreation(UUID applicationId) {
    var membership =
        routes
            .findMembershipByApplicationId(applicationId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "No application group route found for application " + applicationId));
    if (membership.getRouteKind() == ApplicationGroupRouteKind.STANDALONE) {
      var lockedRoute =
          routes
              .findByApplicationIdForUpdate(applicationId)
              .orElseThrow(
                  () ->
                      new ResourceNotFoundException(
                          "No application group route found for application " + applicationId));
      if (lockedRoute.getRouteKind() != ApplicationGroupRouteKind.STANDALONE
          || lockedRoute.getGroupId() != null) {
        throw LinkedApplicationGroupVersionConflictException.groupChanged(applicationId);
      }
      return Optional.empty();
    }
    var groupId = membership.getGroupId();
    if (groupId == null) {
      throw LinkedApplicationGroupVersionConflictException.groupChanged(applicationId);
    }
    var lockedRoutes = routes.findAllByGroupIdForUpdate(groupId);
    var stillMember =
        lockedRoutes.stream()
            .anyMatch(
                lockedRoute ->
                    lockedRoute.getApplicationId().equals(applicationId)
                        && lockedRoute.getRouteKind() == ApplicationGroupRouteKind.LINKED_GROUP
                        && groupId.equals(lockedRoute.getGroupId()));
    if (!stillMember) {
      throw LinkedApplicationGroupVersionConflictException.groupChanged(applicationId);
    }
    return Optional.of(groupId);
  }

  private ApplicationLinkConflictException membershipChanged(UUID applicationId) {
    return new ApplicationLinkConflictException(
        "Linked group membership of application " + applicationId + " changed; retry the request");
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

  private ApplicationLinkConflictException notInGroup(UUID applicationId) {
    return new ApplicationLinkConflictException(
        "Application " + applicationId + " is not in a linked group");
  }

  private boolean isSameLinkedGroup(
      ApplicationGroupRoute sourceRoute, ApplicationGroupRoute targetRoute) {
    return sourceRoute.getRouteKind() == ApplicationGroupRouteKind.LINKED_GROUP
        && targetRoute.getRouteKind() == ApplicationGroupRouteKind.LINKED_GROUP
        && Objects.equals(sourceRoute.getGroupId(), targetRoute.getGroupId());
  }
}
