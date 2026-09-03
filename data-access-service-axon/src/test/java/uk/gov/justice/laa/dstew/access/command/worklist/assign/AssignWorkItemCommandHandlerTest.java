package uk.gov.justice.laa.dstew.access.command.worklist.assign;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.caseworker.CaseworkerRepository;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.route.WorkItemRoute;
import uk.gov.justice.laa.dstew.access.command.worklist.route.WorkItemRouteResolver;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;

class AssignWorkItemCommandHandlerTest {
  @Test
  void dispatchesAnApplicationAssignmentToItsDirectAggregate() {
    CaseworkerRepository caseworkers = org.mockito.Mockito.mock(CaseworkerRepository.class);
    WorkItemRouteResolver routes = org.mockito.Mockito.mock(WorkItemRouteResolver.class);
    CommandGateway gateway = org.mockito.Mockito.mock(CommandGateway.class);
    AssignWorkItemCommandHandler handler =
        new AssignWorkItemCommandHandler(caseworkers, routes, gateway);
    UUID id = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-09-01T10:00:00Z");
    AssignWorkItemCommand command =
        new AssignWorkItemCommand(id, caseworkerId, 0L, "{}", "Assigned", occurredAt);
    when(caseworkers.existsById(caseworkerId)).thenReturn(true);
    when(routes.resolveDirectRoute(id)).thenReturn(route(WorkItemType.APPLICATION, id, occurredAt));

    handler.handle(command);

    verify(gateway)
        .sendAndWait(
            new DirectWorkItemAssignmentCommand(
                id, caseworkerId, 0L, "{}", "Assigned", occurredAt));
  }

  @Test
  void dispatchesPriorAuthorityAssignmentToItsDirectAggregate() {
    CaseworkerRepository caseworkers = org.mockito.Mockito.mock(CaseworkerRepository.class);
    WorkItemRouteResolver routes = org.mockito.Mockito.mock(WorkItemRouteResolver.class);
    CommandGateway gateway = org.mockito.Mockito.mock(CommandGateway.class);
    AssignWorkItemCommandHandler handler =
        new AssignWorkItemCommandHandler(caseworkers, routes, gateway);
    UUID id = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-09-01T10:00:00Z");
    when(caseworkers.existsById(caseworkerId)).thenReturn(true);
    when(routes.resolveDirectRoute(id))
        .thenReturn(route(WorkItemType.PRIOR_AUTHORITY, id, occurredAt));

    handler.handle(new AssignWorkItemCommand(id, caseworkerId, 0L, "{}", "Assigned", occurredAt));

    verify(gateway)
        .sendAndWait(
            new DirectPriorAuthorityWorkItemAssignmentCommand(
                id, caseworkerId, 0L, "{}", "Assigned", occurredAt));
  }

  @Test
  void rejectsAnUnknownCaseworkerWithoutResolvingOrDispatching() {
    CaseworkerRepository caseworkers = org.mockito.Mockito.mock(CaseworkerRepository.class);
    WorkItemRouteResolver routes = org.mockito.Mockito.mock(WorkItemRouteResolver.class);
    CommandGateway gateway = org.mockito.Mockito.mock(CommandGateway.class);
    AssignWorkItemCommandHandler handler =
        new AssignWorkItemCommandHandler(caseworkers, routes, gateway);
    UUID id = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    when(caseworkers.existsById(caseworkerId)).thenReturn(false);

    assertThatThrownBy(
            () ->
                handler.handle(
                    new AssignWorkItemCommand(
                        id, caseworkerId, 0L, "{}", "Assigned", Instant.now())))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("No caseworker found with id: " + caseworkerId);

    verify(routes, never()).resolveDirectRoute(id);
    verify(gateway, never()).sendAndWait(org.mockito.ArgumentMatchers.any());
  }

  private WorkItemRoute route(WorkItemType type, UUID id, Instant occurredAt) {
    return new WorkItemRoute(
        type,
        id,
        uk.gov.justice.laa.dstew.access.command.worklist.route.WorkItemRouteKind.STANDALONE,
        null,
        0L,
        occurredAt);
  }
}
