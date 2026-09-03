package uk.gov.justice.laa.dstew.access.command.worklist;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.caseworker.CaseworkerRepository;
import uk.gov.justice.laa.dstew.access.command.worklist.route.WorkItemRoute;
import uk.gov.justice.laa.dstew.access.command.worklist.route.WorkItemRouteKind;
import uk.gov.justice.laa.dstew.access.command.worklist.route.WorkItemRouteRepository;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;

class WorkItemAssignmentCommandHandlerTest {
  @Test
  void routesAnExistingStandaloneItemToItsDirectAggregate() {
    CaseworkerRepository caseworkers = org.mockito.Mockito.mock(CaseworkerRepository.class);
    WorkItemRouteRepository routes = org.mockito.Mockito.mock(WorkItemRouteRepository.class);
    CommandGateway gateway = org.mockito.Mockito.mock(CommandGateway.class);
    WorkItemAssignmentCommandHandler handler =
        new WorkItemAssignmentCommandHandler(caseworkers, routes, gateway);
    UUID id = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    AssignWorkItemCommand command =
        new AssignWorkItemCommand(id, caseworkerId, 0L, "{}", "", Instant.now());
    when(caseworkers.existsById(caseworkerId)).thenReturn(true);
    when(routes.findByWorkItemId(id))
        .thenReturn(
            Optional.of(
                new WorkItemRoute(
                    WorkItemType.APPLICATION,
                    id,
                    WorkItemRouteKind.STANDALONE,
                    id,
                    null,
                    0L,
                    Instant.now())));

    handler.assign(
        id,
        caseworkerId,
        command.expectedAssignmentVersion(),
        command.serialisedRequest(),
        command.eventDescription(),
        command.occurredAt());

    verify(gateway)
        .sendAndWait(
            new DirectWorkItemAssignmentCommand(
                id, id, caseworkerId, 0L, "{}", "", command.occurredAt()));
  }

  @Test
  void routesPriorAuthorityByItsSubmissionIdToItsDirectAggregate() {
    CaseworkerRepository caseworkers = org.mockito.Mockito.mock(CaseworkerRepository.class);
    WorkItemRouteRepository routes = org.mockito.Mockito.mock(WorkItemRouteRepository.class);
    CommandGateway gateway = org.mockito.Mockito.mock(CommandGateway.class);
    WorkItemAssignmentCommandHandler handler =
        new WorkItemAssignmentCommandHandler(caseworkers, routes, gateway);
    UUID submissionId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-09-01T10:00:00Z");
    when(caseworkers.existsById(caseworkerId)).thenReturn(true);
    when(routes.findByWorkItemId(submissionId))
        .thenReturn(
            Optional.of(
                new WorkItemRoute(
                    WorkItemType.PRIOR_AUTHORITY,
                    submissionId,
                    WorkItemRouteKind.STANDALONE,
                    submissionId,
                    null,
                    0L,
                    occurredAt)));

    handler.assign(submissionId, caseworkerId, 0L, "{}", "", occurredAt);

    verify(gateway)
        .sendAndWait(
            new DirectPriorAuthorityWorkItemAssignmentCommand(
                submissionId, submissionId, caseworkerId, 0L, "{}", "", occurredAt));
  }

  @Test
  void blocksMissingAndNonStandaloneRoutesWithoutFallback() {
    CaseworkerRepository caseworkers = org.mockito.Mockito.mock(CaseworkerRepository.class);
    WorkItemRouteRepository routes = org.mockito.Mockito.mock(WorkItemRouteRepository.class);
    CommandGateway gateway = org.mockito.Mockito.mock(CommandGateway.class);
    WorkItemAssignmentCommandHandler handler =
        new WorkItemAssignmentCommandHandler(caseworkers, routes, gateway);
    UUID id = UUID.randomUUID();
    when(caseworkers.existsById(org.mockito.ArgumentMatchers.any())).thenReturn(true);
    when(routes.findByWorkItemId(id)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                handler.assign(
                    new AssignWorkItemCommand(id, UUID.randomUUID(), 0L, "{}", "", Instant.now())))
        .isInstanceOf(ResourceNotFoundException.class);
  }
}
