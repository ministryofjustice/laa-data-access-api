package uk.gov.justice.laa.dstew.access.command.worklist.unassign;

import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.route.WorkItemRoute;
import uk.gov.justice.laa.dstew.access.command.worklist.route.WorkItemRouteResolver;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;

/** Transactional coordinator that dispatches unassignments without retrying conflicts. */
@Service
public class UnassignWorkItemCoordinator {
  private final WorkItemRouteResolver routeResolver;
  private final CommandGateway commandGateway;

  /** Creates the coordinator with work-item route resolution and command dispatch. */
  public UnassignWorkItemCoordinator(
      WorkItemRouteResolver routeResolver, CommandGateway commandGateway) {
    this.routeResolver = routeResolver;
    this.commandGateway = commandGateway;
  }

  /** Dispatches an explicit direct unassignment; it deliberately never retries a conflict. */
  @Transactional
  @AllowApiCaseworker
  public void handle(UnassignWorkItemCommand command) {
    WorkItemRoute route = routeResolver.resolveDirectRoute(command.workItemId());
    command.expectedAssignment().requireOwner(command.workItemId(), command.workItemId());
    if (route.getWorkItemType() == WorkItemType.PRIOR_AUTHORITY) {
      commandGateway.sendAndWait(
          new DirectPriorAuthorityWorkItemUnassignmentCommand(
              command.workItemId(),
              command.expectedAssignment().version(),
              command.serialisedRequest(),
              command.eventDescription(),
              command.occurredAt()));
      return;
    }

    commandGateway.sendAndWait(
        new DirectWorkItemUnassignmentCommand(
            command.workItemId(),
            command.expectedAssignment().version(),
            command.serialisedRequest(),
            command.eventDescription(),
            command.occurredAt()));
  }
}
