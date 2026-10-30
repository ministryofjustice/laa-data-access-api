package uk.gov.justice.laa.dstew.access.command.worklist.assign;

import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.handler.LinkedGroupWorkItemAssignmentCommandHandler;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationGroupRouteResolver;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.route.WorkItemRoute;
import uk.gov.justice.laa.dstew.access.command.worklist.route.WorkItemRouteResolver;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;

/** Transactional coordinator that dispatches assignments to their authoritative aggregate(s). */
@Service
public class AssignWorkItemCoordinator {
  private final WorkItemRouteResolver routeResolver;
  private final ApplicationGroupRouteResolver groupRouteResolver;
  private final CommandGateway commandGateway;

  /** Creates the coordinator with assignment validation, route resolution, and dispatch. */
  public AssignWorkItemCoordinator(
      WorkItemRouteResolver routeResolver,
      ApplicationGroupRouteResolver groupRouteResolver,
      CommandGateway commandGateway) {
    this.routeResolver = routeResolver;
    this.groupRouteResolver = groupRouteResolver;
    this.commandGateway = commandGateway;
  }

  /**
   * Dispatches the authenticated caseworker assignment to its authoritative aggregate.
   *
   * <p>For an application in a linked group, every active member is assigned in one command; see
   * {@link LinkedGroupWorkItemAssignmentCommandHandler}.
   */
  @Transactional
  @AllowApiCaseworker
  public void handle(AssignWorkItemCommand command) {

    WorkItemRoute route = routeResolver.resolveDirectRoute(command.workItemId());
    if (route.getWorkItemType() == WorkItemType.PRIOR_AUTHORITY) {
      commandGateway.sendAndWait(
          new DirectPriorAuthorityWorkItemAssignmentCommand(
              command.workItemId(),
              command.caseworkerId(),
              command.expectedAssignmentVersion(),
              command.serialisedRequest(),
              command.eventDescription(),
              command.occurredAt()));
      return;
    }

    var groupId = groupRouteResolver.lockForAssignment(command.workItemId());
    if (groupId.isPresent()) {
      commandGateway.sendAndWait(
          new AssignLinkedGroupWorkItemCommand(
              groupId.get(),
              command.workItemId(),
              command.caseworkerId(),
              command.expectedAssignmentVersion(),
              command.serialisedRequest(),
              command.eventDescription(),
              command.occurredAt()));
      return;
    }

    commandGateway.sendAndWait(
        new DirectWorkItemAssignmentCommand(
            command.workItemId(),
            command.caseworkerId(),
            command.expectedAssignmentVersion(),
            command.serialisedRequest(),
            command.eventDescription(),
            command.occurredAt()));
  }
}
