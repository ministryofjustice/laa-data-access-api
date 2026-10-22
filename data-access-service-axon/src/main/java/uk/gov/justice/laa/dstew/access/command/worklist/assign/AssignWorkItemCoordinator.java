package uk.gov.justice.laa.dstew.access.command.worklist.assign;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.AssignLinkedGroupCommand;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.route.WorkItemRoute;
import uk.gov.justice.laa.dstew.access.command.worklist.route.WorkItemRouteKind;
import uk.gov.justice.laa.dstew.access.command.worklist.route.WorkItemRouteResolver;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;

/** Coordinates assignment with the one aggregate that owns the work item's assignment. */
@Service
@RequiredArgsConstructor
public class AssignWorkItemCoordinator {
  private final WorkItemRouteResolver routeResolver;
  private final RetryingCommandDispatcher dispatcher;

  /**
   * Locks the item's routes and dispatches one assignment command to its owning aggregate: the
   * linked group for grouped applications, otherwise the item itself.
   */
  @Transactional
  @AllowApiCaseworker
  public void handle(AssignWorkItemCommand command) {
    WorkItemRoute route = routeResolver.lockRoute(command.workItemId());
    var ownerId =
        route.getRouteKind() == WorkItemRouteKind.LINKED_GROUP
            ? route.getGroupId()
            : command.workItemId();
    command.expectedAssignment().requireOwner(ownerId, command.workItemId());
    long version = command.expectedAssignment().version();

    if (route.getWorkItemType() == WorkItemType.PRIOR_AUTHORITY) {
      dispatcher.dispatch(
          new DirectPriorAuthorityWorkItemAssignmentCommand(
              command.workItemId(),
              command.caseworkerId(),
              version,
              command.serialisedRequest(),
              command.eventDescription(),
              command.occurredAt()));
      return;
    }
    if (route.getRouteKind() == WorkItemRouteKind.LINKED_GROUP) {
      dispatcher.dispatch(
          new AssignLinkedGroupCommand(
              route.getGroupId(),
              command.workItemId(),
              command.caseworkerId(),
              version,
              command.occurredAt()));
      return;
    }
    dispatcher.dispatch(
        new DirectWorkItemAssignmentCommand(
            command.workItemId(),
            command.caseworkerId(),
            version,
            command.serialisedRequest(),
            command.eventDescription(),
            command.occurredAt()));
  }
}
