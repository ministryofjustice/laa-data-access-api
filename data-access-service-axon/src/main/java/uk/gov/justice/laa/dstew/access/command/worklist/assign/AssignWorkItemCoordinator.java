package uk.gov.justice.laa.dstew.access.command.worklist.assign;

import lombok.RequiredArgsConstructor;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.handler.LinkedGroupWorkItemAssignmentCommandHandler;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationGroupRouteResolver;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssignmentConflictException;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.route.WorkItemRoute;
import uk.gov.justice.laa.dstew.access.command.worklist.route.WorkItemRouteResolver;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;

/** Coordinator that locks routes and dispatches assignments to their authoritative aggregate(s). */
@Service
@RequiredArgsConstructor
public class AssignWorkItemCoordinator {
  private final WorkItemRouteResolver routeResolver;
  private final ApplicationGroupRouteResolver groupRouteResolver;
  private final RetryingCommandDispatcher dispatcher;
  private final CommandGateway commandGateway;
  private final PlatformTransactionManager transactionManager;

  /**
   * Dispatches the authenticated caseworker assignment to its authoritative aggregate.
   *
   * <p>For an application in a linked group, every active member is assigned in one command; see
   * {@link LinkedGroupWorkItemAssignmentCommandHandler}. Each attempt locks routes and dispatches
   * in a new transaction; a concurrent-write or lock failure is retried once and then returned as a
   * 409 conflict; a stale assignment version is never retried.
   */
  @AllowApiCaseworker
  public void handle(AssignWorkItemCommand command) {
    var transactionTemplate = new TransactionTemplate(transactionManager);
    transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    try {
      dispatcher.dispatch(
          () -> {
            transactionTemplate.executeWithoutResult(status -> dispatchToAuthority(command));
            return null;
          });
    } catch (RuntimeException failure) {
      if (RetryingCommandDispatcher.isRetryableConcurrentWrite(failure)) {
        throw WorkItemAssignmentConflictException.concurrentChange(command.workItemId(), failure);
      }
      throw failure;
    }
  }

  private void dispatchToAuthority(AssignWorkItemCommand command) {
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
