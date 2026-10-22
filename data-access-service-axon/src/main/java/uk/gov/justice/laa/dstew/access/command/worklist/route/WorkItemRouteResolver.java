package uk.gov.justice.laa.dstew.access.command.worklist.route;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationGroupRouteRepository;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssignmentConflictException;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;

/** Resolves work-item routes and acquires their locks in the documented global order. */
@Service
@RequiredArgsConstructor
public class WorkItemRouteResolver {
  private final WorkItemRouteRepository routes;
  private final ApplicationGroupRouteRepository applicationGroupRoutes;

  /**
   * Peeks at membership, locks its application-group routes first, then locks and verifies the
   * work-item route.
   */
  @Transactional
  public Optional<WorkItemRoute> lockRouteIfPresent(UUID workItemId) {
    var membership = routes.findMembershipByWorkItemId(workItemId);
    if (membership.isEmpty()) {
      return Optional.empty();
    }

    var peek = membership.get();
    if (peek.getWorkItemType() == WorkItemType.APPLICATION) {
      if (peek.getRouteKind() == WorkItemRouteKind.LINKED_GROUP) {
        if (peek.getGroupId() == null) {
          throw membershipChanged(workItemId);
        }
        applicationGroupRoutes.findAllByGroupIdForUpdate(peek.getGroupId());
      } else {
        applicationGroupRoutes.findByApplicationIdForUpdate(workItemId);
      }
    }

    var locked = routes.findByWorkItemIdForUpdate(workItemId);
    if (locked.isEmpty()) {
      return Optional.empty();
    }
    var route = locked.get();
    if (route.getRouteKind() != peek.getRouteKind()
        || !Objects.equals(route.getGroupId(), peek.getGroupId())) {
      throw membershipChanged(workItemId);
    }
    return Optional.of(route);
  }

  /** Locks and returns the work-item route, or throws when the route does not exist. */
  @Transactional
  public WorkItemRoute lockRoute(UUID workItemId) {
    return lockRouteIfPresent(workItemId)
        .orElseThrow(
            () -> new ResourceNotFoundException("No work-item route found for " + workItemId));
  }

  /** Locks existing application work-item routes in ascending application identifier order. */
  @Transactional
  public void lockApplicationRoutes(Collection<UUID> applicationIds) {
    routes.findAllByWorkItemIdInForUpdate(applicationIds.stream().sorted().toList());
  }

  /** Locks all work-item routes currently routed to the supplied group. */
  @Transactional
  public void lockGroupRoutes(UUID groupId) {
    routes.findAllByGroupIdForUpdate(groupId);
  }

  private WorkItemAssignmentConflictException membershipChanged(UUID workItemId) {
    return new WorkItemAssignmentConflictException(
        workItemId, "its linked group membership changed; re-read before retrying");
  }
}
