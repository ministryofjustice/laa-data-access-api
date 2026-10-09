package uk.gov.justice.laa.dstew.access.command.worklist.assign;

import java.util.List;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationGroupRouteResolver;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.route.WorkItemRoute;
import uk.gov.justice.laa.dstew.access.command.worklist.route.WorkItemRouteResolver;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;

/** Validates and dispatches assignment commands to their authoritative aggregate(s). */
@Service
public class AssignWorkItemCommandHandler {
  private final WorkItemRouteResolver routeResolver;
  private final ApplicationGroupRouteResolver groupRouteResolver;
  private final CommandGateway commandGateway;

  /** Creates the command handler with assignment validation, route resolution, and dispatch. */
  public AssignWorkItemCommandHandler(
      WorkItemRouteResolver routeResolver,
      ApplicationGroupRouteResolver groupRouteResolver,
      CommandGateway commandGateway) {
    this.routeResolver = routeResolver;
    this.groupRouteResolver = groupRouteResolver;
    this.commandGateway = commandGateway;
  }

  /**
   * Dispatches the authenticated caseworker assignment to the direct authoritative aggregate.
   *
   * <p>For an Application that belongs to a linked group, the assignment is instead propagated to
   * every member of the group so the group is handled by a single caseworker; the per-item expected
   * assignment version is not checked in that case, since the pessimistic lock taken across the
   * whole group is the concurrency guard.
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

    var groupMemberApplicationIds =
        groupRouteResolver.resolveGroupMembersForAssignment(command.workItemId());
    if (groupMemberApplicationIds.isPresent()) {
      assignGroup(groupMemberApplicationIds.get(), command);
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

  private void assignGroup(List<UUID> memberApplicationIds, AssignWorkItemCommand command) {
    for (UUID memberApplicationId : memberApplicationIds) {
      boolean isTargetedItem = memberApplicationId.equals(command.workItemId());
      commandGateway.sendAndWait(
          new DirectGroupWorkItemAssignmentCommand(
              memberApplicationId,
              command.caseworkerId(),
              isTargetedItem ? command.expectedAssignmentVersion() : null,
              command.serialisedRequest(),
              command.eventDescription(),
              command.occurredAt()));
    }
  }
}
