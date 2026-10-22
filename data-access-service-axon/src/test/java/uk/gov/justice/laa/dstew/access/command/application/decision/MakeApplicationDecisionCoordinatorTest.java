package uk.gov.justice.laa.dstew.access.command.application.decision;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.ValidateLinkedGroupAssigneeCommand;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.route.WorkItemRoute;
import uk.gov.justice.laa.dstew.access.command.worklist.route.WorkItemRouteKind;
import uk.gov.justice.laa.dstew.access.command.worklist.route.WorkItemRouteResolver;

@ExtendWith(MockitoExtension.class)
class MakeApplicationDecisionCoordinatorTest {
  private static final Instant OCCURRED_AT = Instant.parse("2026-09-01T10:00:00Z");

  @Mock private RetryingCommandDispatcher dispatcher;
  @Mock private WorkItemRouteResolver workItemRouteResolver;
  @InjectMocks private MakeApplicationDecisionCoordinator coordinator;

  @Test
  void givenNoWorkItemRoute_whenDecided_thenDispatchesUnchanged() {
    MakeApplicationDecisionCommand command = command(UUID.randomUUID());
    when(workItemRouteResolver.lockRouteIfPresent(command.applicationId()))
        .thenReturn(Optional.empty());

    coordinator.handle(command);

    verify(dispatcher).dispatch(command);
  }

  @Test
  void givenStandaloneRoute_whenDecided_thenDispatchesUnchanged() {
    MakeApplicationDecisionCommand command = command(UUID.randomUUID());
    when(workItemRouteResolver.lockRouteIfPresent(command.applicationId()))
        .thenReturn(
            Optional.of(route(command.applicationId(), WorkItemRouteKind.STANDALONE, null)));

    coordinator.handle(command);

    verify(dispatcher).dispatch(command);
  }

  @Test
  void givenGroupedRoute_whenDecided_thenValidatesGroupThenDecides() {
    MakeApplicationDecisionCommand command = command(UUID.randomUUID());
    UUID groupId = UUID.randomUUID();
    when(workItemRouteResolver.lockRouteIfPresent(command.applicationId()))
        .thenReturn(
            Optional.of(route(command.applicationId(), WorkItemRouteKind.LINKED_GROUP, groupId)));

    coordinator.handle(command);

    InOrder inOrder = inOrder(workItemRouteResolver, dispatcher);
    inOrder.verify(workItemRouteResolver).lockRouteIfPresent(command.applicationId());
    inOrder
        .verify(dispatcher)
        .dispatch(
            new ValidateLinkedGroupAssigneeCommand(
                groupId, command.applicationId(), command.caseworkerId()));
    inOrder.verify(dispatcher).dispatch(command.withGroupAssigneeValidatedFor(groupId));
  }

  @Test
  void givenGroupRejectsAssignee_whenDecided_thenDecisionNotDispatched() {
    MakeApplicationDecisionCommand command = command(UUID.randomUUID());
    UUID groupId = UUID.randomUUID();
    RuntimeException conflict = new RuntimeException("group rejected");
    when(workItemRouteResolver.lockRouteIfPresent(command.applicationId()))
        .thenReturn(
            Optional.of(route(command.applicationId(), WorkItemRouteKind.LINKED_GROUP, groupId)));
    doThrow(conflict)
        .when(dispatcher)
        .dispatch(
            new ValidateLinkedGroupAssigneeCommand(
                groupId, command.applicationId(), command.caseworkerId()));

    assertThatThrownBy(() -> coordinator.handle(command)).isSameAs(conflict);

    verify(dispatcher)
        .dispatch(
            new ValidateLinkedGroupAssigneeCommand(
                groupId, command.applicationId(), command.caseworkerId()));
    verifyNoMoreInteractions(dispatcher);
  }

  private MakeApplicationDecisionCommand command(UUID applicationId) {
    return new MakeApplicationDecisionCommand(
        applicationId,
        UUID.randomUUID(),
        1L,
        "REFUSED",
        List.of(),
        null,
        "{}",
        "decision",
        OCCURRED_AT,
        null);
  }

  private WorkItemRoute route(UUID applicationId, WorkItemRouteKind kind, UUID groupId) {
    return new WorkItemRoute(
        WorkItemType.APPLICATION, applicationId, kind, groupId, 0L, OCCURRED_AT);
  }
}
