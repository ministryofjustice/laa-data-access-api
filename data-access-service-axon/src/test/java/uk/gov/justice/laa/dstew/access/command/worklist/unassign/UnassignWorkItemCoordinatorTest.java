package uk.gov.justice.laa.dstew.access.command.worklist.unassign;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.worklist.ExpectedAssignment;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssignmentConflictException;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.route.WorkItemRoute;
import uk.gov.justice.laa.dstew.access.command.worklist.route.WorkItemRouteKind;
import uk.gov.justice.laa.dstew.access.command.worklist.route.WorkItemRouteResolver;

class UnassignWorkItemCoordinatorTest {
  @Test
  void dispatchesAnApplicationUnassignmentToItsDirectAggregate() {
    WorkItemRouteResolver routes = mock(WorkItemRouteResolver.class);
    CommandGateway gateway = mock(CommandGateway.class);
    UnassignWorkItemCoordinator handler = new UnassignWorkItemCoordinator(routes, gateway);
    UUID id = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-09-01T10:00:00Z");
    when(routes.resolveDirectRoute(id)).thenReturn(route(WorkItemType.APPLICATION, id, occurredAt));

    handler.handle(
        new UnassignWorkItemCommand(
            id, new ExpectedAssignment(id, 1L), "{}", "Unassigned", occurredAt));

    verify(gateway)
        .sendAndWait(new DirectWorkItemUnassignmentCommand(id, 1L, "{}", "Unassigned", occurredAt));
  }

  @Test
  void dispatchesPriorAuthorityUnassignmentToItsDirectAggregate() {
    WorkItemRouteResolver routes = mock(WorkItemRouteResolver.class);
    CommandGateway gateway = mock(CommandGateway.class);
    UnassignWorkItemCoordinator handler = new UnassignWorkItemCoordinator(routes, gateway);
    UUID id = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-09-01T10:00:00Z");
    when(routes.resolveDirectRoute(id))
        .thenReturn(route(WorkItemType.PRIOR_AUTHORITY, id, occurredAt));

    handler.handle(
        new UnassignWorkItemCommand(
            id, new ExpectedAssignment(id, 1L), "{}", "Unassigned", occurredAt));

    verify(gateway)
        .sendAndWait(
            new DirectPriorAuthorityWorkItemUnassignmentCommand(
                id, 1L, "{}", "Unassigned", occurredAt));
  }

  @Test
  void givenTokenForAnotherOwner_whenHandled_thenStaleWithoutDispatch() {
    WorkItemRouteResolver routes = mock(WorkItemRouteResolver.class);
    CommandGateway gateway = mock(CommandGateway.class);
    UnassignWorkItemCoordinator handler = new UnassignWorkItemCoordinator(routes, gateway);
    UUID id = UUID.randomUUID();
    UUID otherOwnerId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-09-01T10:00:00Z");
    when(routes.resolveDirectRoute(id)).thenReturn(route(WorkItemType.APPLICATION, id, occurredAt));

    assertThatThrownBy(
            () ->
                handler.handle(
                    new UnassignWorkItemCommand(
                        id,
                        new ExpectedAssignment(otherOwnerId, 1L),
                        "{}",
                        "Unassigned",
                        occurredAt)))
        .isInstanceOf(WorkItemAssignmentConflictException.class)
        .hasMessage("Work item " + id + " cannot be updated: the assignment version is stale");

    verify(gateway, never()).sendAndWait(org.mockito.ArgumentMatchers.any());
  }

  private WorkItemRoute route(WorkItemType type, UUID id, Instant occurredAt) {
    return new WorkItemRoute(type, id, WorkItemRouteKind.STANDALONE, null, 0L, occurredAt);
  }
}
