package uk.gov.justice.laa.dstew.access.command.worklist.assign;

import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.route.WorkItemRoute;
import uk.gov.justice.laa.dstew.access.command.worklist.route.WorkItemRouteResolver;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;

/** Validates and dispatches assignment commands to their authoritative aggregate. */
@Service
public class AssignWorkItemCommandHandler {
  private final WorkItemRouteResolver routeResolver;
  private final CommandGateway commandGateway;

  /** Creates the command handler with assignment validation, route resolution, and dispatch. */
  public AssignWorkItemCommandHandler(
      WorkItemRouteResolver routeResolver, CommandGateway commandGateway) {
    this.routeResolver = routeResolver;
    this.commandGateway = commandGateway;
  }

  /** Dispatches the authenticated caseworker assignment to the direct authoritative aggregate. */
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
