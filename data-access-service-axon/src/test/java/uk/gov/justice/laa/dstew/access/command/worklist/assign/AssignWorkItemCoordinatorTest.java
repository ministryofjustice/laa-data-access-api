package uk.gov.justice.laa.dstew.access.command.worklist.assign;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationGroupRouteResolver;
import uk.gov.justice.laa.dstew.access.command.worklist.ExpectedAssignment;
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
    AssignWorkItemCoordinator handler = new AssignWorkItemCoordinator(routes, groupRoutes, gateway);
    UUID id = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-09-01T10:00:00Z");
    AssignWorkItemCommand command =
        new AssignWorkItemCommand(
            id, caseworkerId, new ExpectedAssignment(id, 0L), "{}", "Assigned", occurredAt);
    when(routes.resolveDirectRoute(id)).thenReturn(route(WorkItemType.APPLICATION, id, occurredAt));
    when(groupRoutes.resolveGroupMembersForAssignment(id)).thenReturn(Optional.empty());

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
    AssignWorkItemCoordinator handler = new AssignWorkItemCoordinator(routes, groupRoutes, gateway);
    UUID id = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-09-01T10:00:00Z");
    when(routes.resolveDirectRoute(id))
        .thenReturn(route(WorkItemType.PRIOR_AUTHORITY, id, occurredAt));

    handler.handle(
        new AssignWorkItemCommand(
            id, caseworkerId, new ExpectedAssignment(id, 0L), "{}", "Assigned", occurredAt));

    verify(gateway)
        .sendAndWait(
            new DirectPriorAuthorityWorkItemAssignmentCommand(
                id, caseworkerId, 0L, "{}", "Assigned", occurredAt));
    verify(groupRoutes, never())
        .resolveGroupMembersForAssignment(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void propagatesAnUnknownWorkItemWithoutDispatching() {
    WorkItemRouteResolver routes = mock(WorkItemRouteResolver.class);
    ApplicationGroupRouteResolver groupRoutes = mock(ApplicationGroupRouteResolver.class);
    CommandGateway gateway = mock(CommandGateway.class);
    AssignWorkItemCoordinator handler = new AssignWorkItemCoordinator(routes, groupRoutes, gateway);
    UUID id = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    when(routes.resolveDirectRoute(id))
        .thenThrow(new ResourceNotFoundException("No work item found with id: " + id));

    assertThatThrownBy(
            () ->
                handler.handle(
                    new AssignWorkItemCommand(
                        id,
                        caseworkerId,
                        new ExpectedAssignment(id, 0L),
                        "{}",
                        "Assigned",
                        Instant.now())))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("No work item found with id: " + id);

    verify(gateway, never()).sendAndWait(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void propagatesAGroupedApplicationAssignmentToEveryMember() {
    WorkItemRouteResolver routes = mock(WorkItemRouteResolver.class);
    ApplicationGroupRouteResolver groupRoutes = mock(ApplicationGroupRouteResolver.class);
    CommandGateway gateway = mock(CommandGateway.class);
    AssignWorkItemCoordinator handler = new AssignWorkItemCoordinator(routes, groupRoutes, gateway);
    UUID id = UUID.randomUUID();
    UUID siblingId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-09-01T10:00:00Z");
    AssignWorkItemCommand command =
        new AssignWorkItemCommand(
            id, caseworkerId, new ExpectedAssignment(id, 0L), "{}", "Assigned", occurredAt);
    when(routes.resolveDirectRoute(id)).thenReturn(route(WorkItemType.APPLICATION, id, occurredAt));
    when(groupRoutes.resolveGroupMembersForAssignment(id))
        .thenReturn(Optional.of(List.of(id, siblingId)));

    handler.handle(command);

    InOrder order = inOrder(gateway);
    order
        .verify(gateway)
        .sendAndWait(
            new DirectGroupWorkItemAssignmentCommand(
                id, caseworkerId, 0L, "{}", "Assigned", occurredAt));
    order
        .verify(gateway)
        .sendAndWait(
            new DirectGroupWorkItemAssignmentCommand(
                siblingId, caseworkerId, null, "{}", "Assigned", occurredAt));
    verify(gateway, never())
        .sendAndWait(org.mockito.ArgumentMatchers.isA(DirectWorkItemAssignmentCommand.class));
  }

  @Test
  void givenTokenForAnotherOwner_whenHandled_thenStaleWithoutDispatch() {
    WorkItemRouteResolver routes = mock(WorkItemRouteResolver.class);
    ApplicationGroupRouteResolver groupRoutes = mock(ApplicationGroupRouteResolver.class);
    CommandGateway gateway = mock(CommandGateway.class);
    AssignWorkItemCoordinator handler = new AssignWorkItemCoordinator(routes, groupRoutes, gateway);
    UUID id = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    UUID otherOwnerId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-09-01T10:00:00Z");
    when(routes.resolveDirectRoute(id)).thenReturn(route(WorkItemType.APPLICATION, id, occurredAt));

    assertThatThrownBy(
            () ->
                handler.handle(
                    new AssignWorkItemCommand(
                        id,
                        caseworkerId,
                        new ExpectedAssignment(otherOwnerId, 0L),
                        "{}",
                        "Assigned",
                        occurredAt)))
        .isInstanceOf(WorkItemAssignmentConflictException.class)
        .hasMessage("Work item " + id + " cannot be updated: the assignment version is stale");

    verify(gateway, never()).sendAndWait(org.mockito.ArgumentMatchers.any());
  }

  private WorkItemRoute route(WorkItemType type, UUID id, Instant occurredAt) {
    return new WorkItemRoute(type, id, WorkItemRouteKind.STANDALONE, null, 0L, occurredAt);
  }
}
