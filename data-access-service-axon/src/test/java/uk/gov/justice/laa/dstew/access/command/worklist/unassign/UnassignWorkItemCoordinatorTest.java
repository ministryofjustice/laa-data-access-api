package uk.gov.justice.laa.dstew.access.command.worklist.unassign;

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
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.UnassignLinkedGroupCommand;
import uk.gov.justice.laa.dstew.access.command.worklist.ExpectedAssignment;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssignmentConflictException;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.route.WorkItemRoute;
import uk.gov.justice.laa.dstew.access.command.worklist.route.WorkItemRouteKind;
import uk.gov.justice.laa.dstew.access.command.worklist.route.WorkItemRouteResolver;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
class UnassignWorkItemCoordinatorTest {
  private static final Instant OCCURRED_AT = Instant.parse("2026-09-01T10:00:00Z");
  private static final String SERIALISED_REQUEST = "{}";
  private static final String EVENT_DESCRIPTION = "Unassigned";

  @Mock private WorkItemRouteResolver routeResolver;
  @Mock private RetryingCommandDispatcher dispatcher;
  @InjectMocks private UnassignWorkItemCoordinator coordinator;

  @Test
  void givenStandaloneApplication_whenUnassigned_thenDispatchesDirectUnassignment() {
    UUID applicationId = UUID.randomUUID();
    when(routeResolver.lockRoute(applicationId))
        .thenReturn(
            route(applicationId, WorkItemType.APPLICATION, WorkItemRouteKind.STANDALONE, null));

    coordinator.handle(command(applicationId, applicationId, 1L));

    verify(dispatcher)
        .dispatch(
            new DirectWorkItemUnassignmentCommand(
                applicationId, 1L, SERIALISED_REQUEST, EVENT_DESCRIPTION, OCCURRED_AT));
  }

  @Test
  void givenPriorAuthority_whenUnassigned_thenDispatchesPriorAuthorityUnassignment() {
    UUID priorAuthorityId = UUID.randomUUID();
    when(routeResolver.lockRoute(priorAuthorityId))
        .thenReturn(
            route(
                priorAuthorityId,
                WorkItemType.PRIOR_AUTHORITY,
                WorkItemRouteKind.STANDALONE,
                null));

    coordinator.handle(command(priorAuthorityId, priorAuthorityId, 1L));

    verify(dispatcher)
        .dispatch(
            new DirectPriorAuthorityWorkItemUnassignmentCommand(
                priorAuthorityId, 1L, SERIALISED_REQUEST, EVENT_DESCRIPTION, OCCURRED_AT));
  }

  @Test
  void givenGroupedApplication_whenUnassigned_thenDispatchesOneGroupCommand() {
    UUID applicationId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    when(routeResolver.lockRoute(applicationId))
        .thenReturn(
            route(
                applicationId, WorkItemType.APPLICATION, WorkItemRouteKind.LINKED_GROUP, groupId));

    coordinator.handle(command(applicationId, groupId, 3L));

    verify(dispatcher)
        .dispatch(new UnassignLinkedGroupCommand(groupId, applicationId, 3L, OCCURRED_AT));
    verifyNoMoreInteractions(dispatcher);
  }

  @Test
  void givenItemTokenForGroupedApplication_whenUnassigned_thenStaleWithoutDispatch() {
    UUID applicationId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    when(routeResolver.lockRoute(applicationId))
        .thenReturn(
            route(
                applicationId, WorkItemType.APPLICATION, WorkItemRouteKind.LINKED_GROUP, groupId));

    assertThatThrownBy(() -> coordinator.handle(command(applicationId, applicationId, 3L)))
        .isInstanceOf(WorkItemAssignmentConflictException.class)
        .hasMessage(
            "Work item " + applicationId + " cannot be updated: the assignment version is stale");

    verifyNoInteractions(dispatcher);
  }

  @Test
  void givenUnknownWorkItem_whenUnassigned_thenNotFoundWithoutDispatch() {
    UUID applicationId = UUID.randomUUID();
    when(routeResolver.lockRoute(applicationId))
        .thenThrow(new ResourceNotFoundException("No work-item route found for " + applicationId));

    assertThatThrownBy(() -> coordinator.handle(command(applicationId, applicationId, 0L)))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("No work-item route found for " + applicationId);

    verifyNoInteractions(dispatcher);
  }

  private UnassignWorkItemCommand command(UUID workItemId, UUID ownerId, long version) {
    return new UnassignWorkItemCommand(
        workItemId,
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
