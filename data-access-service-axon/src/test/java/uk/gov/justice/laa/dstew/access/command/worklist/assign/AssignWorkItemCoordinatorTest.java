package uk.gov.justice.laa.dstew.access.command.worklist.assign;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.AssignLinkedGroupCommand;
import uk.gov.justice.laa.dstew.access.command.worklist.ExpectedAssignment;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssignmentConflictException;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.route.WorkItemRoute;
import uk.gov.justice.laa.dstew.access.command.worklist.route.WorkItemRouteKind;
import uk.gov.justice.laa.dstew.access.command.worklist.route.WorkItemRouteResolver;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
class AssignWorkItemCoordinatorTest {
  private static final Instant OCCURRED_AT = Instant.parse("2026-09-01T10:00:00Z");
  private static final String SERIALISED_REQUEST = "{}";
  private static final String EVENT_DESCRIPTION = "Assigned";

  @Mock private WorkItemRouteResolver routeResolver;
  @Mock private RetryingCommandDispatcher dispatcher;
  @InjectMocks private AssignWorkItemCoordinator coordinator;

  @Test
  void givenStandaloneApplication_whenAssigned_thenDispatchesDirectAssignment() {
    UUID applicationId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    when(routeResolver.lockRoute(applicationId))
        .thenReturn(
            route(applicationId, WorkItemType.APPLICATION, WorkItemRouteKind.STANDALONE, null));

    coordinator.handle(command(applicationId, caseworkerId, applicationId, 0L));

    verify(dispatcher)
        .dispatch(
            new DirectWorkItemAssignmentCommand(
                applicationId,
                caseworkerId,
                0L,
                SERIALISED_REQUEST,
                EVENT_DESCRIPTION,
                OCCURRED_AT));
  }

  @Test
  void givenPriorAuthority_whenAssigned_thenDispatchesPriorAuthorityAssignment() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    when(routeResolver.lockRoute(priorAuthorityId))
        .thenReturn(
            route(
                priorAuthorityId,
                WorkItemType.PRIOR_AUTHORITY,
                WorkItemRouteKind.STANDALONE,
                null));

    coordinator.handle(command(priorAuthorityId, caseworkerId, priorAuthorityId, 0L));

    verify(dispatcher)
        .dispatch(
            new DirectPriorAuthorityWorkItemAssignmentCommand(
                priorAuthorityId,
                caseworkerId,
                0L,
                SERIALISED_REQUEST,
                EVENT_DESCRIPTION,
                OCCURRED_AT));
  }

  @Test
  void givenGroupedApplication_whenAssigned_thenDispatchesOneGroupCommand() {
    UUID applicationId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    when(routeResolver.lockRoute(applicationId))
        .thenReturn(
            route(
                applicationId, WorkItemType.APPLICATION, WorkItemRouteKind.LINKED_GROUP, groupId));

    coordinator.handle(command(applicationId, caseworkerId, groupId, 3L));

    verify(dispatcher)
        .dispatch(
            new AssignLinkedGroupCommand(groupId, applicationId, caseworkerId, 3L, OCCURRED_AT));
    verifyNoMoreInteractions(dispatcher);
  }

  @Test
  void givenItemTokenForGroupedApplication_whenAssigned_thenStaleWithoutDispatch() {
    UUID applicationId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    when(routeResolver.lockRoute(applicationId))
        .thenReturn(
            route(
                applicationId, WorkItemType.APPLICATION, WorkItemRouteKind.LINKED_GROUP, groupId));

    assertThatThrownBy(
            () -> coordinator.handle(command(applicationId, caseworkerId, applicationId, 3L)))
        .isInstanceOf(WorkItemAssignmentConflictException.class)
        .hasMessage(
            "Work item " + applicationId + " cannot be updated: the assignment version is stale");

    verifyNoInteractions(dispatcher);
  }

  @Test
  void givenUnknownWorkItem_whenAssigned_thenNotFoundWithoutDispatch() {
    UUID applicationId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    when(routeResolver.lockRoute(applicationId))
        .thenThrow(new ResourceNotFoundException("No work-item route found for " + applicationId));

    assertThatThrownBy(
            () -> coordinator.handle(command(applicationId, caseworkerId, applicationId, 0L)))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("No work-item route found for " + applicationId);

    verifyNoInteractions(dispatcher);
  }

  private AssignWorkItemCommand command(
      UUID workItemId, UUID caseworkerId, UUID ownerId, long version) {
    return new AssignWorkItemCommand(
        workItemId,
        caseworkerId,
        new ExpectedAssignment(ownerId, version),
        SERIALISED_REQUEST,
        EVENT_DESCRIPTION,
        OCCURRED_AT);
  }

  private WorkItemRoute route(
      UUID workItemId, WorkItemType type, WorkItemRouteKind kind, UUID groupId) {
    return new WorkItemRoute(type, workItemId, kind, groupId, 0L, OCCURRED_AT);
  }
}
