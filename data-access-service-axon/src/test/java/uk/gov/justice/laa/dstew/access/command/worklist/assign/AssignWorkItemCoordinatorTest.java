package uk.gov.justice.laa.dstew.access.command.worklist.assign;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.axonframework.modelling.ConcurrencyException;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationGroupRouteResolver;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.route.WorkItemRoute;
import uk.gov.justice.laa.dstew.access.command.worklist.route.WorkItemRouteKind;
import uk.gov.justice.laa.dstew.access.command.worklist.route.WorkItemRouteResolver;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;

class AssignWorkItemCoordinatorTest {
  @Test
  void dispatchesAnApplicationAssignmentToItsDirectAggregate() {
    WorkItemRouteResolver routes = mock(WorkItemRouteResolver.class);
    ApplicationGroupRouteResolver groupRoutes = mock(ApplicationGroupRouteResolver.class);
    CommandGateway gateway = mock(CommandGateway.class);
    AssignWorkItemCoordinator handler =
        coordinator(routes, groupRoutes, gateway, mock(PlatformTransactionManager.class));
    UUID id = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-09-01T10:00:00Z");
    AssignWorkItemCommand command =
        new AssignWorkItemCommand(id, caseworkerId, 0L, "{}", "Assigned", occurredAt);
    when(routes.resolveDirectRoute(id)).thenReturn(route(WorkItemType.APPLICATION, id, occurredAt));
    when(groupRoutes.lockForAssignment(id)).thenReturn(Optional.empty());

    handler.handle(command);

    verify(gateway)
        .sendAndWait(
            new DirectWorkItemAssignmentCommand(
                id, caseworkerId, 0L, "{}", "Assigned", occurredAt));
  }

  @Test
  void dispatchesPriorAuthorityAssignmentToItsDirectAggregate() {
    WorkItemRouteResolver routes = mock(WorkItemRouteResolver.class);
    ApplicationGroupRouteResolver groupRoutes = mock(ApplicationGroupRouteResolver.class);
    CommandGateway gateway = mock(CommandGateway.class);
    AssignWorkItemCoordinator handler =
        coordinator(routes, groupRoutes, gateway, mock(PlatformTransactionManager.class));
    UUID id = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-09-01T10:00:00Z");
    when(routes.resolveDirectRoute(id))
        .thenReturn(route(WorkItemType.PRIOR_AUTHORITY, id, occurredAt));

    handler.handle(new AssignWorkItemCommand(id, caseworkerId, 0L, "{}", "Assigned", occurredAt));

    verify(gateway)
        .sendAndWait(
            new DirectPriorAuthorityWorkItemAssignmentCommand(
                id, caseworkerId, 0L, "{}", "Assigned", occurredAt));
    verify(groupRoutes, never()).lockForAssignment(any());
  }

  @Test
  void propagatesAnUnknownWorkItemWithoutDispatching() {
    WorkItemRouteResolver routes = mock(WorkItemRouteResolver.class);
    ApplicationGroupRouteResolver groupRoutes = mock(ApplicationGroupRouteResolver.class);
    CommandGateway gateway = mock(CommandGateway.class);
    AssignWorkItemCoordinator handler =
        coordinator(routes, groupRoutes, gateway, mock(PlatformTransactionManager.class));
    UUID id = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    when(routes.resolveDirectRoute(id))
        .thenThrow(new ResourceNotFoundException("No work item found with id: " + id));

    assertThatThrownBy(
            () ->
                handler.handle(
                    new AssignWorkItemCommand(
                        id, caseworkerId, 0L, "{}", "Assigned", Instant.now())))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("No work item found with id: " + id);

    verify(gateway, never()).sendAndWait(any());
  }

  @Test
  void givenGroupedApplication_whenAssigned_thenSendsOneGroupCommand() {
    WorkItemRouteResolver routes = mock(WorkItemRouteResolver.class);
    ApplicationGroupRouteResolver groupRoutes = mock(ApplicationGroupRouteResolver.class);
    CommandGateway gateway = mock(CommandGateway.class);
    AssignWorkItemCoordinator handler =
        coordinator(routes, groupRoutes, gateway, mock(PlatformTransactionManager.class));
    UUID id = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-09-01T10:00:00Z");
    AssignWorkItemCommand command =
        new AssignWorkItemCommand(id, caseworkerId, 0L, "{}", "Assigned", occurredAt);
    when(routes.resolveDirectRoute(id)).thenReturn(route(WorkItemType.APPLICATION, id, occurredAt));
    when(groupRoutes.lockForAssignment(id)).thenReturn(Optional.of(groupId));

    handler.handle(command);

    verify(gateway)
        .sendAndWait(
            new AssignLinkedGroupWorkItemCommand(
                groupId, id, caseworkerId, 0L, "{}", "Assigned", occurredAt));
    verify(gateway, never()).sendAndWait(isA(DirectWorkItemAssignmentCommand.class));
  }

  @Test
  void givenAppendConflict_whenAssigned_thenRetriesInFreshTransaction() {
    WorkItemRouteResolver routes = mock(WorkItemRouteResolver.class);
    ApplicationGroupRouteResolver groupRoutes = mock(ApplicationGroupRouteResolver.class);
    CommandGateway gateway = mock(CommandGateway.class);
    PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    AssignWorkItemCoordinator handler =
        coordinator(routes, groupRoutes, gateway, transactionManager);
    UUID id = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-09-01T10:00:00Z");
    when(routes.resolveDirectRoute(id)).thenReturn(route(WorkItemType.APPLICATION, id, occurredAt));
    when(groupRoutes.lockForAssignment(id)).thenReturn(Optional.of(groupId));
    AssignLinkedGroupWorkItemCommand groupCommand =
        new AssignLinkedGroupWorkItemCommand(
            groupId, id, caseworkerId, 0L, "{}", "Assigned", occurredAt);
    when(gateway.sendAndWait(groupCommand))
        .thenThrow(new RuntimeException("append conflict", new ConcurrencyException("conflict")))
        .thenReturn(null);

    handler.handle(new AssignWorkItemCommand(id, caseworkerId, 0L, "{}", "Assigned", occurredAt));

    verify(transactionManager, times(2)).getTransaction(any(TransactionDefinition.class));
    verify(transactionManager).rollback(any(TransactionStatus.class));
    verify(transactionManager).commit(any(TransactionStatus.class));
    verify(groupRoutes, times(2)).lockForAssignment(id);
    verify(gateway, times(2)).sendAndWait(groupCommand);
  }

  private AssignWorkItemCoordinator coordinator(
      WorkItemRouteResolver routes,
      ApplicationGroupRouteResolver groupRoutes,
      CommandGateway gateway,
      PlatformTransactionManager transactionManager) {
    when(transactionManager.getTransaction(any(TransactionDefinition.class)))
        .thenAnswer(invocation -> new SimpleTransactionStatus());
    return new AssignWorkItemCoordinator(
        routes, groupRoutes, new RetryingCommandDispatcher(gateway), gateway, transactionManager);
  }

  private WorkItemRoute route(WorkItemType type, UUID id, Instant occurredAt) {
    return new WorkItemRoute(type, id, WorkItemRouteKind.STANDALONE, null, 0L, occurredAt);
  }
}
