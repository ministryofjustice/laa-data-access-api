package uk.gov.justice.laa.dstew.access.command.worklist.unassign;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
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
        new UnassignWorkItemCoordinator(routes, groupRoutes, gateway);
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
        new UnassignWorkItemCoordinator(routes, groupRoutes, gateway);
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
        new UnassignWorkItemCoordinator(routes, groupRoutes, gateway);
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

  private WorkItemRoute route(WorkItemType type, UUID id, Instant occurredAt) {
    return new WorkItemRoute(type, id, WorkItemRouteKind.STANDALONE, null, 0L, occurredAt);
  }
}
