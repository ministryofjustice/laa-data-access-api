package uk.gov.justice.laa.dstew.access.command.worklist.unassign;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.axonframework.modelling.ConcurrencyException;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
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

class UnassignWorkItemCoordinatorTest {
  @Test
  void givenStandaloneApplication_whenUnassigned_thenLocksBeforeDispatch() {
    WorkItemRouteResolver routes = mock(WorkItemRouteResolver.class);
    ApplicationGroupRouteResolver groupRoutes = mock(ApplicationGroupRouteResolver.class);
    CommandGateway gateway = mock(CommandGateway.class);
    UnassignWorkItemCoordinator handler =
        coordinator(routes, groupRoutes, gateway, mock(PlatformTransactionManager.class));
    UUID id = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-09-01T10:00:00Z");
    when(routes.resolveDirectRoute(id)).thenReturn(route(WorkItemType.APPLICATION, id, occurredAt));
    when(groupRoutes.lockForAssignment(id)).thenReturn(Optional.empty());

    handler.handle(new UnassignWorkItemCommand(id, 1L, "{}", "Unassigned", occurredAt));

    InOrder order = inOrder(groupRoutes, gateway);
    order.verify(groupRoutes).lockForAssignment(id);
    order
        .verify(gateway)
        .sendAndWait(new DirectWorkItemUnassignmentCommand(id, 1L, "{}", "Unassigned", occurredAt));
  }

  @Test
  void dispatchesPriorAuthorityUnassignmentToItsDirectAggregate() {
    WorkItemRouteResolver routes = mock(WorkItemRouteResolver.class);
    ApplicationGroupRouteResolver groupRoutes = mock(ApplicationGroupRouteResolver.class);
    CommandGateway gateway = mock(CommandGateway.class);
    UnassignWorkItemCoordinator handler =
        coordinator(routes, groupRoutes, gateway, mock(PlatformTransactionManager.class));
    UUID id = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-09-01T10:00:00Z");
    when(routes.resolveDirectRoute(id))
        .thenReturn(route(WorkItemType.PRIOR_AUTHORITY, id, occurredAt));

    handler.handle(new UnassignWorkItemCommand(id, 1L, "{}", "Unassigned", occurredAt));

    verify(gateway)
        .sendAndWait(
            new DirectPriorAuthorityWorkItemUnassignmentCommand(
                id, 1L, "{}", "Unassigned", occurredAt));
  }

  @Test
  void givenGroupedApplication_whenUnassigned_thenSendsOneGroupCommand() {
    WorkItemRouteResolver routes = mock(WorkItemRouteResolver.class);
    ApplicationGroupRouteResolver groupRoutes = mock(ApplicationGroupRouteResolver.class);
    CommandGateway gateway = mock(CommandGateway.class);
    UnassignWorkItemCoordinator handler =
        coordinator(routes, groupRoutes, gateway, mock(PlatformTransactionManager.class));
    UUID id = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-09-01T10:00:00Z");
    when(routes.resolveDirectRoute(id)).thenReturn(route(WorkItemType.APPLICATION, id, occurredAt));
    when(groupRoutes.lockForAssignment(id)).thenReturn(Optional.of(groupId));

    handler.handle(new UnassignWorkItemCommand(id, 1L, "{}", "Unassigned", occurredAt));

    verify(gateway)
        .sendAndWait(
            new UnassignLinkedGroupWorkItemCommand(
                groupId, id, 1L, "{}", "Unassigned", occurredAt));
  }

  @Test
  void givenAppendConflict_whenUnassigned_thenRetriesInFreshTransaction() {
    WorkItemRouteResolver routes = mock(WorkItemRouteResolver.class);
    ApplicationGroupRouteResolver groupRoutes = mock(ApplicationGroupRouteResolver.class);
    CommandGateway gateway = mock(CommandGateway.class);
    PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    UnassignWorkItemCoordinator handler =
        coordinator(routes, groupRoutes, gateway, transactionManager);
    UUID id = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-09-01T10:00:00Z");
    when(routes.resolveDirectRoute(id)).thenReturn(route(WorkItemType.APPLICATION, id, occurredAt));
    when(groupRoutes.lockForAssignment(id)).thenReturn(Optional.of(groupId));
    UnassignLinkedGroupWorkItemCommand groupCommand =
        new UnassignLinkedGroupWorkItemCommand(groupId, id, 1L, "{}", "Unassigned", occurredAt);
    when(gateway.sendAndWait(groupCommand))
        .thenThrow(new RuntimeException("append conflict", new ConcurrencyException("conflict")))
        .thenReturn(null);

    handler.handle(new UnassignWorkItemCommand(id, 1L, "{}", "Unassigned", occurredAt));

    verify(transactionManager, times(2)).getTransaction(any(TransactionDefinition.class));
    verify(transactionManager).rollback(any(TransactionStatus.class));
    verify(transactionManager).commit(any(TransactionStatus.class));
    verify(groupRoutes, times(2)).lockForAssignment(id);
    verify(gateway, times(2)).sendAndWait(groupCommand);
  }

  private UnassignWorkItemCoordinator coordinator(
      WorkItemRouteResolver routes,
      ApplicationGroupRouteResolver groupRoutes,
      CommandGateway gateway,
      PlatformTransactionManager transactionManager) {
    when(transactionManager.getTransaction(any(TransactionDefinition.class)))
        .thenAnswer(invocation -> new SimpleTransactionStatus());
    return new UnassignWorkItemCoordinator(
        routes, groupRoutes, new RetryingCommandDispatcher(gateway), gateway, transactionManager);
  }

  private WorkItemRoute route(WorkItemType type, UUID id, Instant occurredAt) {
    return new WorkItemRoute(type, id, WorkItemRouteKind.STANDALONE, null, 0L, occurredAt);
  }
}
