package uk.gov.justice.laa.dstew.access.command.worklist.assign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
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
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssignmentConflictException;
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

    DirectWorkItemAssignmentCommand direct =
        new DirectWorkItemAssignmentCommand(id, caseworkerId, 0L, "{}", "Assigned", occurredAt);
    AssignedWorkItems assigned = assigned(id);
    when(gateway.sendAndWait(direct, AssignedWorkItems.class)).thenReturn(assigned);

    assertThat(handler.handle(command)).isEqualTo(assigned);
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

    DirectPriorAuthorityWorkItemAssignmentCommand direct =
        new DirectPriorAuthorityWorkItemAssignmentCommand(
            id, caseworkerId, 0L, "{}", "Assigned", occurredAt);
    AssignedWorkItems assigned = assigned(id);
    when(gateway.sendAndWait(direct, AssignedWorkItems.class)).thenReturn(assigned);

    assertThat(
            handler.handle(
                new AssignWorkItemCommand(id, caseworkerId, 0L, "{}", "Assigned", occurredAt)))
        .isEqualTo(assigned);

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

    verify(gateway, never()).sendAndWait(any(), eq(AssignedWorkItems.class));
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

    AssignLinkedGroupWorkItemCommand groupCommand =
        new AssignLinkedGroupWorkItemCommand(
            groupId, id, caseworkerId, 0L, "{}", "Assigned", occurredAt);
    AssignedWorkItems assigned = assigned(id);
    when(gateway.sendAndWait(groupCommand, AssignedWorkItems.class)).thenReturn(assigned);

    assertThat(handler.handle(command)).isEqualTo(assigned);

    verify(gateway, never())
        .sendAndWait(isA(DirectWorkItemAssignmentCommand.class), eq(AssignedWorkItems.class));
  }

  @Test
  void givenAppendConflict_whenAssigned_thenRetriesInFreshTransaction() {
    WorkItemRouteResolver routes = mock(WorkItemRouteResolver.class);
    ApplicationGroupRouteResolver groupRoutes = mock(ApplicationGroupRouteResolver.class);
    CommandGateway gateway = mock(CommandGateway.class);
    PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    final AssignWorkItemCoordinator handler =
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
    AssignedWorkItems assigned = assigned(id);
    when(gateway.sendAndWait(groupCommand, AssignedWorkItems.class))
        .thenThrow(new RuntimeException("append conflict", new ConcurrencyException("conflict")))
        .thenReturn(assigned);

    assertThat(
            handler.handle(
                new AssignWorkItemCommand(id, caseworkerId, 0L, "{}", "Assigned", occurredAt)))
        .isEqualTo(assigned);

    verify(transactionManager, times(2)).getTransaction(any(TransactionDefinition.class));
    verify(transactionManager).rollback(any(TransactionStatus.class));
    verify(transactionManager).commit(any(TransactionStatus.class));
    verify(groupRoutes, times(2)).lockForAssignment(id);
    verify(gateway, times(2)).sendAndWait(groupCommand, AssignedWorkItems.class);
  }

  @Test
  void givenAppendConflictTwice_whenAssigned_thenAssignmentConflict() {
    WorkItemRouteResolver routes = mock(WorkItemRouteResolver.class);
    ApplicationGroupRouteResolver groupRoutes = mock(ApplicationGroupRouteResolver.class);
    CommandGateway gateway = mock(CommandGateway.class);
    AssignWorkItemCoordinator handler =
        coordinator(routes, groupRoutes, gateway, mock(PlatformTransactionManager.class));
    UUID id = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-09-01T10:00:00Z");
    when(routes.resolveDirectRoute(id)).thenReturn(route(WorkItemType.APPLICATION, id, occurredAt));
    when(groupRoutes.lockForAssignment(id)).thenReturn(Optional.of(groupId));
    RuntimeException retryFailure =
        new RuntimeException("append conflict", new ConcurrencyException("second"));
    when(gateway.sendAndWait(
            new AssignLinkedGroupWorkItemCommand(
                groupId, id, caseworkerId, 0L, "{}", "Assigned", occurredAt),
            AssignedWorkItems.class))
        .thenThrow(new RuntimeException("append conflict", new ConcurrencyException("first")))
        .thenThrow(retryFailure);

    assertThatThrownBy(
            () ->
                handler.handle(
                    new AssignWorkItemCommand(id, caseworkerId, 0L, "{}", "Assigned", occurredAt)))
        .isInstanceOf(WorkItemAssignmentConflictException.class)
        .hasMessage(
            "Work item "
                + id
                + " cannot be updated: it was changed by a concurrent request; retry the request")
        .hasCause(retryFailure);
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

  private AssignedWorkItems assigned(UUID id) {
    return new AssignedWorkItems(List.of(new AssignedWorkItems.AssignedItem(id, "LAA-123")));
  }

  private WorkItemRoute route(WorkItemType type, UUID id, Instant occurredAt) {
    return new WorkItemRoute(type, id, WorkItemRouteKind.STANDALONE, null, 0L, occurredAt);
  }
}
