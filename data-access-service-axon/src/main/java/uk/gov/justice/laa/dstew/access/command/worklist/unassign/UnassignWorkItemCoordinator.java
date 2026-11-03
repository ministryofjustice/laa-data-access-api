package uk.gov.justice.laa.dstew.access.command.worklist.unassign;

import lombok.RequiredArgsConstructor;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationGroupRouteResolver;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.route.WorkItemRoute;
import uk.gov.justice.laa.dstew.access.command.worklist.route.WorkItemRouteResolver;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;

/** Coordinator that locks routes and dispatches unassignments to their authoritative aggregate. */
@Service
@RequiredArgsConstructor
public class UnassignWorkItemCoordinator {
  private final WorkItemRouteResolver routeResolver;
  private final ApplicationGroupRouteResolver groupRouteResolver;
  private final RetryingCommandDispatcher dispatcher;
  private final CommandGateway commandGateway;
  private final PlatformTransactionManager transactionManager;

  /**
   * Dispatches an explicit unassignment. Each attempt locks routes and dispatches in a new
   * transaction; a concurrent-write or lock failure is retried once, and a stale assignment version
   * is never retried.
   */
  @AllowApiCaseworker
  public void handle(UnassignWorkItemCommand command) {
    var transactionTemplate = new TransactionTemplate(transactionManager);
    transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    dispatcher.dispatch(
        () -> {
          transactionTemplate.executeWithoutResult(status -> dispatchToAuthority(command));
          return null;
        });
  }

  private void dispatchToAuthority(UnassignWorkItemCommand command) {
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

    var groupId = groupRouteResolver.lockForAssignment(command.workItemId());
    if (groupId.isPresent()) {
      commandGateway.sendAndWait(
          new UnassignLinkedGroupWorkItemCommand(
              groupId.get(),
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
