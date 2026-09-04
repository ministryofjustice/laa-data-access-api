package uk.gov.justice.laa.dstew.access.command.worklist.unassign;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.route.WorkItemRoute;
import uk.gov.justice.laa.dstew.access.command.worklist.route.WorkItemRouteKind;
import uk.gov.justice.laa.dstew.access.command.worklist.route.WorkItemRouteResolver;

class UnassignWorkItemCommandHandlerTest {
  @Test
  void dispatchesAnApplicationUnassignmentToItsDirectAggregate() {
    WorkItemRouteResolver routes = org.mockito.Mockito.mock(WorkItemRouteResolver.class);
    CommandGateway gateway = org.mockito.Mockito.mock(CommandGateway.class);
    UnassignWorkItemCommandHandler handler = new UnassignWorkItemCommandHandler(routes, gateway);
    UUID id = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-09-01T10:00:00Z");
    when(routes.resolveDirectRoute(id)).thenReturn(route(WorkItemType.APPLICATION, id, occurredAt));

    handler.handle(new UnassignWorkItemCommand(id, 1L, "{}", "Unassigned", occurredAt));

    verify(gateway)
        .sendAndWait(new DirectWorkItemUnassignmentCommand(id, 1L, "{}", "Unassigned", occurredAt));
  }

  @Test
  void dispatchesPriorAuthorityUnassignmentToItsDirectAggregate() {
    WorkItemRouteResolver routes = org.mockito.Mockito.mock(WorkItemRouteResolver.class);
    CommandGateway gateway = org.mockito.Mockito.mock(CommandGateway.class);
    UnassignWorkItemCommandHandler handler = new UnassignWorkItemCommandHandler(routes, gateway);
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

  private WorkItemRoute route(WorkItemType type, UUID id, Instant occurredAt) {
    return new WorkItemRoute(type, id, WorkItemRouteKind.STANDALONE, null, 0L, occurredAt);
  }
}
