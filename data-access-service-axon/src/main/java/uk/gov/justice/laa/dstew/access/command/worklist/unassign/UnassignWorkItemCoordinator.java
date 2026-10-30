package uk.gov.justice.laa.dstew.access.command.worklist.unassign;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.UnassignLinkedGroupCommand;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.route.WorkItemRoute;
import uk.gov.justice.laa.dstew.access.command.worklist.route.WorkItemRouteKind;
import uk.gov.justice.laa.dstew.access.command.worklist.route.WorkItemRouteResolver;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;

/** Coordinates unassignment with the one aggregate that owns the work item's assignment. */
@Service
@RequiredArgsConstructor
public class UnassignWorkItemCoordinator {
  private final WorkItemRouteResolver routeResolver;
  private final RetryingCommandDispatcher dispatcher;

  /** Locks the item's routes and dispatches one unassignment command to its assignment owner. */
  @Transactional
  @AllowApiCaseworker
  public void handle(UnassignWorkItemCommand command) {
    WorkItemRoute route = routeResolver.lockRoute(command.workItemId());
    var ownerId =
        route.getRouteKind() == WorkItemRouteKind.LINKED_GROUP
            ? route.getGroupId()
            : command.workItemId();
    command.expectedAssignment().requireOwner(ownerId, command.workItemId());
    long version = command.expectedAssignment().version();

    if (route.getWorkItemType() == WorkItemType.PRIOR_AUTHORITY) {
      dispatcher.dispatch(
          new DirectPriorAuthorityWorkItemUnassignmentCommand(
              command.workItemId(),
              version,
              command.serialisedRequest(),
              command.eventDescription(),
              command.occurredAt()));
      return;
    }
    if (route.getRouteKind() == WorkItemRouteKind.LINKED_GROUP) {
      dispatcher.dispatch(
          new UnassignLinkedGroupCommand(
              route.getGroupId(), command.workItemId(), version, command.occurredAt()));
      return;
    }
    dispatcher.dispatch(
        new DirectWorkItemUnassignmentCommand(
            command.workItemId(),
            version,
            command.serialisedRequest(),
            command.eventDescription(),
            command.occurredAt()));
  }
}
