package uk.gov.justice.laa.dstew.access.command.worklist;

import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.access.command.caseworker.CaseworkerRepository;
import uk.gov.justice.laa.dstew.access.command.worklist.route.WorkItemRoute;
import uk.gov.justice.laa.dstew.access.command.worklist.route.WorkItemRouteKind;
import uk.gov.justice.laa.dstew.access.command.worklist.route.WorkItemRouteRepository;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;

/** Resolves a durable direct route before dispatching generic work-list assignment commands. */
@Service
public class WorkItemAssignmentCommandHandler {
  private final CaseworkerRepository caseworkers;
  private final WorkItemRouteRepository routes;
  private final CommandGateway commandGateway;

  /** Creates the orchestrator with command-side authority lookup dependencies. */
  public WorkItemAssignmentCommandHandler(
      CaseworkerRepository caseworkers,
      WorkItemRouteRepository routes,
      CommandGateway commandGateway) {
    this.caseworkers = caseworkers;
    this.routes = routes;
    this.commandGateway = commandGateway;
  }

  /** Validates the target caseworker and dispatches to the direct authoritative aggregate. */
  @Transactional
  @AllowApiCaseworker
  public void assign(AssignWorkItemCommand command) {
    assign(command, route(command.workItemId()));
  }

  /** Resolves a public ID-only assignment request to its one authoritative work-item route. */
  @Transactional
  @AllowApiCaseworker
  public void assign(
      java.util.UUID itemId,
      java.util.UUID caseworkerId,
      long expectedAssignmentVersion,
      String serialisedRequest,
      String eventDescription,
      java.time.Instant occurredAt) {
    WorkItemRoute route = route(itemId);
    assign(
        new AssignWorkItemCommand(
            itemId,
            caseworkerId,
            expectedAssignmentVersion,
            serialisedRequest,
            eventDescription,
            occurredAt),
        route);
  }

  private void assign(AssignWorkItemCommand command, WorkItemRoute route) {
    if (!caseworkers.existsById(command.caseworkerId())) {
      throw new ResourceNotFoundException("No caseworker found with id: " + command.caseworkerId());
    }
    requireDirectRoute(route, command.workItemId());
    if (route.getWorkItemType() == WorkItemType.PRIOR_AUTHORITY) {
      commandGateway.sendAndWait(
          new DirectPriorAuthorityWorkItemAssignmentCommand(
              route.getAggregateId(),
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
            route.getAggregateId(),
            command.workItemId(),
            command.caseworkerId(),
            command.expectedAssignmentVersion(),
            command.serialisedRequest(),
            command.eventDescription(),
            command.occurredAt()));
  }

  /** Dispatches an explicit direct unassignment; it deliberately never retries a conflict. */
  @Transactional
  @AllowApiCaseworker
  public void unassign(UnassignWorkItemCommand command) {
    unassign(command, route(command.workItemId()));
  }

  /** Resolves a public ID-only unassignment request to its one authoritative work-item route. */
  @Transactional
  @AllowApiCaseworker
  public void unassign(
      java.util.UUID itemId,
      long expectedAssignmentVersion,
      String serialisedRequest,
      String eventDescription,
      java.time.Instant occurredAt) {
    WorkItemRoute route = route(itemId);
    unassign(
        new UnassignWorkItemCommand(
            itemId, expectedAssignmentVersion, serialisedRequest, eventDescription, occurredAt),
        route);
  }

  private void unassign(UnassignWorkItemCommand command, WorkItemRoute route) {
    requireDirectRoute(route, command.workItemId());
    if (route.getWorkItemType() == WorkItemType.PRIOR_AUTHORITY) {
      commandGateway.sendAndWait(
          new DirectPriorAuthorityWorkItemUnassignmentCommand(
              route.getAggregateId(),
              command.workItemId(),
              command.expectedAssignmentVersion(),
              command.serialisedRequest(),
              command.eventDescription(),
              command.occurredAt()));
      return;
    }
    commandGateway.sendAndWait(
        new DirectWorkItemUnassignmentCommand(
            route.getAggregateId(),
            command.workItemId(),
            command.expectedAssignmentVersion(),
            command.serialisedRequest(),
            command.eventDescription(),
            command.occurredAt()));
  }

  private WorkItemRoute route(java.util.UUID itemId) {
    return routes
        .findByWorkItemId(itemId)
        .orElseThrow(() -> new ResourceNotFoundException("No work-item route found for " + itemId));
  }

  private void requireDirectRoute(WorkItemRoute route, java.util.UUID workItemId) {
    if (route.getRouteKind() != WorkItemRouteKind.STANDALONE
        || !route.getAggregateId().equals(workItemId)) {
      throw new ResourceNotFoundException("No direct work-item route found for " + workItemId);
    }
  }
}
