package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.justice.laa.dstew.access.command.application.ApplicationStateEvents.activeAssigned;
import static uk.gov.justice.laa.dstew.access.command.application.ApplicationStateEvents.activeUnassigned;
import static uk.gov.justice.laa.dstew.access.command.application.ApplicationStateEvents.stateOf;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.StateManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationAggregate;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationState;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.handler.LinkedGroupWorkItemAssignmentCommandHandler;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationLinkConflictException;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssignmentConflictException;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemUnassigned;
import uk.gov.justice.laa.dstew.access.command.worklist.assign.AssignLinkedGroupWorkItemCommand;
import uk.gov.justice.laa.dstew.access.command.worklist.unassign.UnassignLinkedGroupWorkItemCommand;

@ExtendWith(MockitoExtension.class)
class LinkedGroupWorkItemAssignmentCommandHandlerTest {
  private static final UUID GROUP_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID FIRST_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final UUID SECOND_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
  private static final UUID CASEWORKER_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
  private static final UUID OTHER_CASEWORKER_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000005");
  private static final Instant OCCURRED_AT = Instant.parse("2026-10-27T12:00:00Z");

  @Mock private StateManager stateManager;
  @Mock private ProcessingContext context;
  @Mock private EventAppender eventAppender;
  @Mock private LinkedApplicationGroupAggregate group;
  @Mock private LinkedApplicationGroupState groupState;

  private LinkedGroupWorkItemAssignmentCommandHandler handler;

  @BeforeEach
  void setUp() {
    handler = new LinkedGroupWorkItemAssignmentCommandHandler();
  }

  private void givenExistingGroup() {
    when(group.getState()).thenReturn(groupState);
    when(groupState.getGroupId()).thenReturn(GROUP_ID);
    when(groupState.getMemberApplicationIds()).thenReturn(List.of(SECOND_ID, FIRST_ID));
  }

  @Test
  void givenUnassignedGroup_whenAssign_thenAddressedThenOtherMembersAssignedInOneAppend() {
    givenExistingGroup();
    var firstState = stateOf(activeUnassigned(FIRST_ID));
    var secondState = stateOf(activeUnassigned(SECOND_ID));
    givenApplication(FIRST_ID, firstState);
    givenApplication(SECOND_ID, secondState);
    var command =
        new AssignLinkedGroupWorkItemCommand(
            GROUP_ID, SECOND_ID, CASEWORKER_ID, 0L, "{}", "Assigned", OCCURRED_AT);

    handler.handle(command, group, stateManager, context, eventAppender);

    verify(eventAppender)
        .append(
            List.of(
                new WorkItemAssigned(
                    SECOND_ID, WorkItemType.APPLICATION, 1L, 1L, CASEWORKER_ID, OCCURRED_AT),
                new WorkItemAssigned(
                    FIRST_ID, WorkItemType.APPLICATION, 1L, 1L, CASEWORKER_ID, OCCURRED_AT)));
  }

  @Test
  void givenStaleVersion_whenAssign_thenConflictAndNoEvents() {
    givenExistingGroup();
    givenApplication(FIRST_ID, stateOf(activeUnassigned(FIRST_ID)));
    givenApplication(SECOND_ID, stateOf(activeUnassigned(SECOND_ID)));
    var command =
        new AssignLinkedGroupWorkItemCommand(
            GROUP_ID, FIRST_ID, CASEWORKER_ID, 1L, "{}", "Assigned", OCCURRED_AT);

    assertThatThrownBy(() -> handler.handle(command, group, stateManager, context, eventAppender))
        .isInstanceOf(WorkItemAssignmentConflictException.class)
        .hasMessageContaining("the assignment version is stale");

    verifyNoInteractions(eventAppender);
  }

  @Test
  void givenMissingGroup_whenAssign_thenMembershipConflictAndNoEvents() {
    var command =
        new AssignLinkedGroupWorkItemCommand(
            GROUP_ID, FIRST_ID, CASEWORKER_ID, 0L, "{}", "Assigned", OCCURRED_AT);

    assertThatThrownBy(() -> handler.handle(command, null, stateManager, context, eventAppender))
        .isInstanceOf(ApplicationLinkConflictException.class)
        .hasMessageContaining("membership of application " + FIRST_ID + " changed");

    verifyNoInteractions(stateManager, eventAppender);
  }

  @Test
  void givenAssignedGroup_whenUnassign_thenEveryAssignedMemberUnassigned() {
    givenExistingGroup();
    var firstState = stateOf(activeAssigned(FIRST_ID, CASEWORKER_ID));
    var secondState = stateOf(activeAssigned(SECOND_ID, OTHER_CASEWORKER_ID));
    givenApplication(FIRST_ID, firstState);
    givenApplication(SECOND_ID, secondState);
    var command =
        new UnassignLinkedGroupWorkItemCommand(
            GROUP_ID, SECOND_ID, 1L, "{}", "Unassigned", OCCURRED_AT);

    handler.handle(command, group, stateManager, context, eventAppender);

    verify(eventAppender)
        .append(
            List.of(
                new WorkItemUnassigned(SECOND_ID, WorkItemType.APPLICATION, 1L, 2L, OCCURRED_AT),
                new WorkItemUnassigned(FIRST_ID, WorkItemType.APPLICATION, 1L, 2L, OCCURRED_AT)));
  }

  @Test
  void givenMissingGroup_whenUnassign_thenMembershipConflictAndNoEvents() {
    var command =
        new UnassignLinkedGroupWorkItemCommand(
            GROUP_ID, FIRST_ID, 0L, "{}", "Unassigned", OCCURRED_AT);

    assertThatThrownBy(() -> handler.handle(command, null, stateManager, context, eventAppender))
        .isInstanceOf(ApplicationLinkConflictException.class)
        .hasMessageContaining("membership of application " + FIRST_ID + " changed");

    verifyNoInteractions(stateManager, eventAppender);
  }

  private void givenApplication(UUID applicationId, ApplicationState state) {
    var aggregate = mock(ApplicationAggregate.class);
    when(aggregate.getState()).thenReturn(state);
    when(stateManager.loadEntity(ApplicationAggregate.class, applicationId, context))
        .thenReturn(CompletableFuture.completedFuture(aggregate));
  }
}
