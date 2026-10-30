package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.DelegateAssignmentToGroupCommand;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.DelegatedAssignment;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationGroupRouteResolver;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationLinkAction;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationLinkConflictException;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationLinkPlan;
import uk.gov.justice.laa.dstew.access.command.worklist.route.WorkItemRouteResolver;
import uk.gov.justice.laa.dstew.access.exception.LinkedApplicationGroupVersionConflictException;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

@ExtendWith(MockitoExtension.class)
class LinkApplicationCoordinatorTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-09-14T15:00:00Z");

  @Mock private ApplicationGroupRouteResolver routeResolver;
  @Mock private WorkItemRouteResolver workItemRouteResolver;
  @Mock private RetryingCommandDispatcher dispatcher;

  @InjectMocks private LinkApplicationCoordinator handler;

  private UUID sourceApplicationId;
  private UUID targetApplicationId;
  private static final long GROUP_VERSION = 4L;

  @BeforeEach
  void setUp() {
    sourceApplicationId = UUID.fromString("00000000-0000-0000-0000-000000000101");
    targetApplicationId = UUID.fromString("00000000-0000-0000-0000-000000000202");
  }

  @Test
  void rejectsSelfLinkBeforeRouteLookup() {
    var command =
        new LinkApplicationCommand(
            sourceApplicationId, sourceApplicationId, LinkType.FAMILY, null, OCCURRED_AT);

    assertThatThrownBy(() -> handler.handle(command))
        .isInstanceOfSatisfying(
            ValidationException.class,
            exception ->
                assertThat(exception.errors())
                    .containsExactly("Source and target application IDs must be different"));

    verifyNoInteractions(routeResolver, workItemRouteResolver, dispatcher);
  }

  @Test
  void rejectsNullLinkTypeBeforeRouteLookup() {
    var command =
        new LinkApplicationCommand(
            sourceApplicationId, targetApplicationId, null, null, OCCURRED_AT);

    assertThatThrownBy(() -> handler.handle(command))
        .isInstanceOfSatisfying(
            ValidationException.class,
            exception ->
                assertThat(exception.errors()).containsExactly("linkType: must not be null"));

    verifyNoInteractions(routeResolver, workItemRouteResolver, dispatcher);
  }

  @Test
  void givenAlreadyLinkedApplications_whenLinkedAgain_thenDispatchesNoAggregateCommand() {
    UUID existingGroupId = UUID.fromString("10000000-0000-0000-0000-000000000001");
    when(routeResolver.resolve(sourceApplicationId, targetApplicationId))
        .thenReturn(new ApplicationLinkPlan(ApplicationLinkAction.ALREADY_LINKED, existingGroupId));

    handler.handle(linkCommand(new ExpectedLinkedGroup(UUID.randomUUID(), GROUP_VERSION)));

    verify(routeResolver).resolve(sourceApplicationId, targetApplicationId);
    verifyNoInteractions(workItemRouteResolver, dispatcher);
  }

  @Test
  void givenStandaloneTarget_whenLinked_thenDelegatesBothThenEstablishes() {
    when(routeResolver.resolve(sourceApplicationId, targetApplicationId))
        .thenReturn(new ApplicationLinkPlan(ApplicationLinkAction.CREATE_GROUP, null));
    UUID targetCaseworkerId = UUID.randomUUID();
    when(dispatcher.dispatch(
            any(DelegateAssignmentToGroupCommand.class), eq(DelegatedAssignment.class)))
        .thenAnswer(
            invocation -> {
              DelegateAssignmentToGroupCommand delegate = invocation.getArgument(0);
              return new DelegatedAssignment(
                  delegate.applicationId().equals(targetApplicationId) ? targetCaseworkerId : null);
            });

    handler.handle(linkCommand(null));

    ArgumentCaptor<DelegateAssignmentToGroupCommand> delegateCaptor =
        ArgumentCaptor.forClass(DelegateAssignmentToGroupCommand.class);
    ArgumentCaptor<EstablishLinkedApplicationGroupCommand> establishCaptor =
        ArgumentCaptor.forClass(EstablishLinkedApplicationGroupCommand.class);
    InOrder inOrder = inOrder(routeResolver, workItemRouteResolver, dispatcher);
    inOrder.verify(routeResolver).resolve(sourceApplicationId, targetApplicationId);
    inOrder
        .verify(workItemRouteResolver)
        .lockApplicationRoutes(List.of(sourceApplicationId, targetApplicationId));
    inOrder
        .verify(dispatcher, times(2))
        .dispatch(delegateCaptor.capture(), eq(DelegatedAssignment.class));
    inOrder.verify(dispatcher).dispatch(establishCaptor.capture());

    assertThat(delegateCaptor.getAllValues())
        .extracting(DelegateAssignmentToGroupCommand::applicationId)
        .containsExactly(sourceApplicationId, targetApplicationId);
    assertThat(delegateCaptor.getAllValues())
        .extracting(DelegateAssignmentToGroupCommand::groupId)
        .containsOnly(establishCaptor.getValue().groupId());
    assertThat(establishCaptor.getValue().groupId()).isNotNull();
    assertThat(establishCaptor.getValue().leadApplicationId()).isEqualTo(targetApplicationId);
    assertThat(establishCaptor.getValue().memberApplicationIds())
        .containsExactly(targetApplicationId, sourceApplicationId);
    assertThat(establishCaptor.getValue().joiningAssignments())
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                targetApplicationId, new DelegatedAssignment(targetCaseworkerId),
                sourceApplicationId, new DelegatedAssignment(null)));
    assertThat(establishCaptor.getValue().occurredAt()).isEqualTo(OCCURRED_AT);
    verifyNoMoreInteractions(dispatcher);
  }

  @Test
  void givenVersionForStandaloneTarget_whenLinked_thenThrowsConflict() {
    when(routeResolver.resolve(sourceApplicationId, targetApplicationId))
        .thenReturn(new ApplicationLinkPlan(ApplicationLinkAction.CREATE_GROUP, null));

    assertThatThrownBy(
            () ->
                handler.handle(
                    linkCommand(new ExpectedLinkedGroup(UUID.randomUUID(), GROUP_VERSION))))
        .isInstanceOfSatisfying(
            LinkedApplicationGroupVersionConflictException.class,
            exception ->
                assertThat(exception.getMessage())
                    .isEqualTo(
                        "Application "
                            + targetApplicationId
                            + " is no longer in a linked group; re-read before linking"));

    verifyNoInteractions(workItemRouteResolver, dispatcher);
  }

  @Test
  void givenVersionedGroupedTarget_whenLinked_thenDelegatesSourceThenAdds() {
    UUID existingGroupId = UUID.fromString("10000000-0000-0000-0000-000000000002");
    when(routeResolver.resolve(sourceApplicationId, targetApplicationId))
        .thenReturn(
            new ApplicationLinkPlan(ApplicationLinkAction.ADD_TO_EXISTING_GROUP, existingGroupId));
    DelegatedAssignment assignment = new DelegatedAssignment(UUID.randomUUID());
    when(dispatcher.dispatch(
            new DelegateAssignmentToGroupCommand(sourceApplicationId, existingGroupId, OCCURRED_AT),
            DelegatedAssignment.class))
        .thenReturn(assignment);

    handler.handle(linkCommand(new ExpectedLinkedGroup(existingGroupId, GROUP_VERSION)));

    InOrder inOrder = inOrder(routeResolver, workItemRouteResolver, dispatcher);
    inOrder.verify(routeResolver).resolve(sourceApplicationId, targetApplicationId);
    inOrder.verify(workItemRouteResolver).lockApplicationRoutes(List.of(sourceApplicationId));
    inOrder
        .verify(dispatcher)
        .dispatch(
            new DelegateAssignmentToGroupCommand(sourceApplicationId, existingGroupId, OCCURRED_AT),
            DelegatedAssignment.class);
    inOrder
        .verify(dispatcher)
        .dispatch(
            new AddApplicationToLinkedGroupCommand(
                existingGroupId, sourceApplicationId, GROUP_VERSION, assignment, OCCURRED_AT));
  }

  @Test
  void givenMissingVersionForGroupedTarget_whenLinked_thenThrowsConflict() {
    UUID existingGroupId = UUID.fromString("10000000-0000-0000-0000-000000000003");
    when(routeResolver.resolve(sourceApplicationId, targetApplicationId))
        .thenReturn(
            new ApplicationLinkPlan(ApplicationLinkAction.ADD_TO_EXISTING_GROUP, existingGroupId));

    assertThatThrownBy(() -> handler.handle(linkCommand(null)))
        .isInstanceOfSatisfying(
            LinkedApplicationGroupVersionConflictException.class,
            exception ->
                assertThat(exception.getMessage())
                    .isEqualTo(
                        "Application "
                            + targetApplicationId
                            + " is in a linked group; linkedGroupVersion is required"));

    verifyNoInteractions(workItemRouteResolver, dispatcher);
  }

  @Test
  void givenTokenForDifferentGroup_whenLinkedToGroupedTarget_thenThrowsConflict() {
    UUID existingGroupId = UUID.fromString("10000000-0000-0000-0000-000000000004");
    UUID expectedGroupId = UUID.fromString("20000000-0000-0000-0000-000000000004");
    when(routeResolver.resolve(sourceApplicationId, targetApplicationId))
        .thenReturn(
            new ApplicationLinkPlan(ApplicationLinkAction.ADD_TO_EXISTING_GROUP, existingGroupId));

    assertThatThrownBy(
            () ->
                handler.handle(
                    linkCommand(new ExpectedLinkedGroup(expectedGroupId, GROUP_VERSION))))
        .isInstanceOfSatisfying(
            LinkedApplicationGroupVersionConflictException.class,
            exception ->
                assertThat(exception.getMessage())
                    .isEqualTo(
                        "Application "
                            + targetApplicationId
                            + " is no longer in the linked group identified by linkedGroupVersion;"
                            + " re-read before retrying"));

    verifyNoInteractions(workItemRouteResolver, dispatcher);
  }

  @Test
  void propagatesResolverNotFoundExceptionUnchanged() {
    var exception =
        new ResourceNotFoundException(
            "No application group route found for target application " + targetApplicationId);
    when(routeResolver.resolve(sourceApplicationId, targetApplicationId)).thenThrow(exception);

    assertThatThrownBy(() -> handler.handle(linkCommand(null))).isSameAs(exception);

    verifyNoInteractions(workItemRouteResolver, dispatcher);
  }

  @Test
  void propagatesResolverConflictExceptionUnchanged() {
    var exception =
        new ApplicationLinkConflictException(
            "Application " + sourceApplicationId + " already belongs to a different linked group");
    when(routeResolver.resolve(sourceApplicationId, targetApplicationId)).thenThrow(exception);

    assertThatThrownBy(() -> handler.handle(linkCommand(null))).isSameAs(exception);

    verifyNoInteractions(workItemRouteResolver, dispatcher);
  }

  private LinkApplicationCommand linkCommand(ExpectedLinkedGroup expectedGroup) {
    return new LinkApplicationCommand(
        sourceApplicationId, targetApplicationId, LinkType.FAMILY, expectedGroup, OCCURRED_AT);
  }
}
