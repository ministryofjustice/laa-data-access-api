package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.ReclaimAssignmentFromGroupCommand;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationGroupRouteResolver;
import uk.gov.justice.laa.dstew.access.command.worklist.route.WorkItemRouteResolver;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
class UnlinkApplicationCoordinatorTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-09-14T15:00:00Z");

  @Mock private ApplicationGroupRouteResolver routeResolver;
  @Mock private WorkItemRouteResolver workItemRouteResolver;
  @Mock private RetryingCommandDispatcher dispatcher;

  @InjectMocks private UnlinkApplicationCoordinator handler;

  @Test
  void givenRemoval_whenUnlinked_thenReclaimsLeavingMemberAfterGroup() {
    var applicationId = UUID.randomUUID();
    var groupId = UUID.randomUUID();
    var caseworkerId = UUID.randomUUID();
    var command =
        new UnlinkApplicationCommand(
            applicationId, new ExpectedLinkedGroup(groupId, 4), OCCURRED_AT);
    when(routeResolver.resolveGroupForMutation(applicationId, groupId)).thenReturn(groupId);
    when(dispatcher.dispatch(
            new RemoveApplicationFromLinkedGroupCommand(groupId, applicationId, 4, OCCURRED_AT),
            LinkedGroupRemovalResult.class))
        .thenReturn(
            new LinkedGroupRemovalResult(groupId, List.of(applicationId), caseworkerId, 7L));

    handler.handle(command);

    InOrder inOrder = inOrder(routeResolver, workItemRouteResolver, dispatcher);
    inOrder.verify(routeResolver).resolveGroupForMutation(applicationId, groupId);
    inOrder.verify(workItemRouteResolver).lockGroupRoutes(groupId);
    inOrder
        .verify(dispatcher)
        .dispatch(
            new RemoveApplicationFromLinkedGroupCommand(groupId, applicationId, 4, OCCURRED_AT),
            LinkedGroupRemovalResult.class);
    inOrder
        .verify(dispatcher)
        .dispatch(
            new ReclaimAssignmentFromGroupCommand(
                applicationId, groupId, caseworkerId, 7L, OCCURRED_AT));
  }

  @Test
  void givenDissolution_whenUnlinked_thenReclaimsBothInAscendingOrder() {
    UUID applicationId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    UUID firstApplicationId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    UUID groupId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    var command =
        new UnlinkApplicationCommand(
            applicationId, new ExpectedLinkedGroup(groupId, 4), OCCURRED_AT);
    when(routeResolver.resolveGroupForMutation(applicationId, groupId)).thenReturn(groupId);
    when(dispatcher.dispatch(
            new RemoveApplicationFromLinkedGroupCommand(groupId, applicationId, 4, OCCURRED_AT),
            LinkedGroupRemovalResult.class))
        .thenReturn(
            new LinkedGroupRemovalResult(
                groupId, List.of(firstApplicationId, applicationId), caseworkerId, 7L));

    handler.handle(command);

    InOrder inOrder = inOrder(dispatcher);
    inOrder
        .verify(dispatcher)
        .dispatch(
            new RemoveApplicationFromLinkedGroupCommand(groupId, applicationId, 4, OCCURRED_AT),
            LinkedGroupRemovalResult.class);
    inOrder
        .verify(dispatcher)
        .dispatch(
            new ReclaimAssignmentFromGroupCommand(
                firstApplicationId, groupId, caseworkerId, 7L, OCCURRED_AT));
    inOrder
        .verify(dispatcher)
        .dispatch(
            new ReclaimAssignmentFromGroupCommand(
                applicationId, groupId, caseworkerId, 7L, OCCURRED_AT));
  }

  @Test
  void givenRouteResolverFails_whenHandled_thenPropagatesAndDoesNotDispatch() {
    var applicationId = UUID.randomUUID();
    var groupId = UUID.randomUUID();
    var command =
        new UnlinkApplicationCommand(
            applicationId, new ExpectedLinkedGroup(groupId, 4), OCCURRED_AT);
    var exception = new ResourceNotFoundException("route not found");
    when(routeResolver.resolveGroupForMutation(applicationId, groupId)).thenThrow(exception);

    assertThatThrownBy(() -> handler.handle(command)).isSameAs(exception);

    verifyNoInteractions(workItemRouteResolver, dispatcher);
  }
}
