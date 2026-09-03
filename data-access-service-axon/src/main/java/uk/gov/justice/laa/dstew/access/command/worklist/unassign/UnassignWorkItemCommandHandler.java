package uk.gov.justice.laa.dstew.access.command.worklist.unassign;

import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.route.WorkItemRoute;
import uk.gov.justice.laa.dstew.access.command.worklist.route.WorkItemRouteResolver;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;

/** Dispatches unassignment commands to their authoritative aggregate without retrying conflicts. */
@Service
public class UnassignWorkItemCommandHandler {
  private final WorkItemRouteResolver routeResolver;
  private final CommandGateway commandGateway;

  public UnassignWorkItemCommandHandler(
      WorkItemRouteResolver routeResolver, CommandGateway commandGateway) {
    this.routeResolver = routeResolver;
    this.commandGateway = commandGateway;
  }

  /** Dispatches an explicit direct unassignment; it deliberately never retries a conflict. */
  @Transactional
  @AllowApiCaseworker
  public void handle(UnassignWorkItemCommand command) {
    WorkItemRoute route = routeResolver.resolveDirectRoute(command.workItemId());
    if (route.getWorkItemType() == WorkItemType.PRIOR_AUTHORITY) {
      commandGateway.sendAndWait(
          new DirectPriorAuthorityWorkItemUnassignmentCommand(
              command.workItemId(),
              command.expectedAssignmentVersion(),
              command.serialisedRequest(),
              command.eventDescription(),
              command.occurredAt()));
      return;
    }

    commandGateway.sendAndWait(
        new DirectWorkItemUnassignmentCommand(
            command.workItemId(),
            command.expectedAssignmentVersion(),
            command.serialisedRequest(),
            command.eventDescription(),
            command.occurredAt()));
  }
}
