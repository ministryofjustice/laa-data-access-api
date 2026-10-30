package uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
    var lockedRoutes =
        routes.findAllByApplicationIdInForUpdate(
            Stream.of(sourceApplicationId, targetApplicationId).sorted().toList());
    var routesByApplicationId =
        lockedRoutes.stream()
            .collect(
                Collectors.toMap(ApplicationGroupRoute::getApplicationId, Function.identity()));
    var sourceRoute = requiredRoute(routesByApplicationId, sourceApplicationId, "source");
    var targetRoute = requiredRoute(routesByApplicationId, targetApplicationId, "target");

    if (!sourceRoute.hasSameOfficeCodeAs(targetRoute)) {
      throw new ValidationException(List.of("Applications must have the same office code"));
    }

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
    return lockCurrentGroupRoutes(
        applicationId,
        membership,
        () -> LinkedApplicationGroupVersionConflictException.groupChanged(applicationId));
  }

  /** Locks the current group membership before a readiness command is recorded. */
  @Transactional
  public Optional<UUID> lockGroupRoutesForReadiness(UUID applicationId) {
    var membership = routes.findMembershipByApplicationId(applicationId);
    if (membership.isEmpty()) {
      return Optional.empty();
    }
    return lockCurrentGroupRoutes(
        applicationId,
        membership.get(),
        () ->
            new ApplicationLinkConflictException(
                "Linked group membership of application "
                    + applicationId
                    + " changed; retry the request"));
  }

  private Optional<UUID> lockCurrentGroupRoutes(
      UUID applicationId,
      ApplicationGroupRouteMembership membership,
      Supplier<RuntimeException> membershipChanged) {
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
        throw membershipChanged.get();
      }
      return Optional.empty();
    }
    var groupId = membership.getGroupId();
    if (groupId == null) {
      throw membershipChanged.get();
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
      throw membershipChanged.get();
    }
    return Optional.of(groupId);
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
