package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.justice.laa.dstew.access.command.application.ApplicationStateEvents.activeAssigned;
import static uk.gov.justice.laa.dstew.access.command.application.ApplicationStateEvents.activeUnassigned;
import static uk.gov.justice.laa.dstew.access.command.application.ApplicationStateEvents.autoGranted;
import static uk.gov.justice.laa.dstew.access.command.application.ApplicationStateEvents.decidedAssigned;
import static uk.gov.justice.laa.dstew.access.command.application.ApplicationStateEvents.stateOf;

import java.time.Instant;
import java.util.List;
import java.util.Map;
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
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataId;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.handler.LinkedGroupWorkItemAssignmentCommandHandler;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationLinkConflictException;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssignmentConflictException;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemUnassigned;
import uk.gov.justice.laa.dstew.access.command.worklist.assign.AssignLinkedGroupWorkItemCommand;
import uk.gov.justice.laa.dstew.access.command.worklist.assign.AssignedWorkItems;
import uk.gov.justice.laa.dstew.access.command.worklist.unassign.UnassignLinkedGroupWorkItemCommand;

@ExtendWith(MockitoExtension.class)
class LinkedGroupWorkItemAssignmentCommandHandlerTest {
  private static final UUID GROUP_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID FIRST_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final UUID SECOND_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
  private static final UUID CASEWORKER_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
  private static final UUID OTHER_CASEWORKER_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000005");
  private static final UUID THIRD_ID = UUID.fromString("00000000-0000-0000-0000-000000000006");
  private static final Instant OCCURRED_AT = Instant.parse("2026-10-27T12:00:00Z");

  @Mock private StateManager stateManager;
  @Mock private ProcessingContext context;
  @Mock private EventAppender eventAppender;
  @Mock private ApplicationDataStore applicationDataStore;
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
    when(groupState.memberIdsOrEmpty()).thenReturn(List.of(SECOND_ID, FIRST_ID));
  }

  @Test
  void givenUnassignedGroup_whenAssign_thenAddressedThenOtherMembersAssignedInOneAppend() {
    givenExistingGroup();
    var firstState = stateOf(activeUnassigned(FIRST_ID));
    var secondState = stateOf(activeUnassigned(SECOND_ID));
    givenApplication(FIRST_ID, firstState);
    givenApplication(SECOND_ID, secondState);
    var secondDataId = new ApplicationDataId(SECOND_ID, secondState.getApplicationDataVersion());
    var firstDataId = new ApplicationDataId(FIRST_ID, firstState.getApplicationDataVersion());
    var secondPayload = mock(ApplicationDataPayload.class);
    var firstPayload = mock(ApplicationDataPayload.class);
    when(secondPayload.laaReference()).thenReturn("LAA-SECOND");
    when(firstPayload.laaReference()).thenReturn("LAA-FIRST");
    when(applicationDataStore.getAll(List.of(secondDataId, firstDataId)))
        .thenReturn(Map.of(secondDataId, secondPayload, firstDataId, firstPayload));
    var command =
        new AssignLinkedGroupWorkItemCommand(
            GROUP_ID, SECOND_ID, CASEWORKER_ID, 0L, "{}", "Assigned", OCCURRED_AT);

    var result =
        handler.handle(command, group, stateManager, context, applicationDataStore, eventAppender);

    verify(eventAppender)
        .append(
            List.of(
                new WorkItemAssigned(
                    SECOND_ID, WorkItemType.APPLICATION, 1L, 1L, CASEWORKER_ID, OCCURRED_AT),
                new WorkItemAssigned(
                    FIRST_ID, WorkItemType.APPLICATION, 1L, 1L, CASEWORKER_ID, OCCURRED_AT)));
    assertThat(result.items())
        .containsExactly(
            new AssignedWorkItems.AssignedItem(SECOND_ID, "LAA-SECOND"),
            new AssignedWorkItems.AssignedItem(FIRST_ID, "LAA-FIRST"));
  }

  @Test
  void givenMemberAlreadyAssignedToCaseworker_whenAssign_thenOnlyChangedMembersReturned() {
    givenExistingGroup();
    var firstState = stateOf(activeAssigned(FIRST_ID, CASEWORKER_ID));
    var secondState = stateOf(activeUnassigned(SECOND_ID));
    givenApplication(FIRST_ID, firstState);
    givenApplication(SECOND_ID, secondState);
    var secondDataId = new ApplicationDataId(SECOND_ID, secondState.getApplicationDataVersion());
    var secondPayload = mock(ApplicationDataPayload.class);
    when(secondPayload.laaReference()).thenReturn("LAA-SECOND");
    when(applicationDataStore.getAll(List.of(secondDataId)))
        .thenReturn(Map.of(secondDataId, secondPayload));
    var command =
        new AssignLinkedGroupWorkItemCommand(
            GROUP_ID, SECOND_ID, CASEWORKER_ID, 0L, "{}", "Assigned", OCCURRED_AT);

    var result =
        handler.handle(command, group, stateManager, context, applicationDataStore, eventAppender);

    assertThat(result.items())
        .containsExactly(new AssignedWorkItems.AssignedItem(SECOND_ID, "LAA-SECOND"));
  }

  @Test
  void givenDecidedAndAutoGrantedMembers_whenAssign_thenOnlyActiveMemberReturned() {
    when(group.getState()).thenReturn(groupState);
    when(groupState.getGroupId()).thenReturn(GROUP_ID);
    when(groupState.memberIdsOrEmpty()).thenReturn(List.of(SECOND_ID, FIRST_ID, THIRD_ID));
    var addressedState = stateOf(activeUnassigned(SECOND_ID));
    givenApplication(SECOND_ID, addressedState);
    givenApplication(FIRST_ID, stateOf(autoGranted(FIRST_ID)));
    givenApplication(THIRD_ID, stateOf(decidedAssigned(THIRD_ID, OTHER_CASEWORKER_ID)));
    var addressedDataId =
        new ApplicationDataId(SECOND_ID, addressedState.getApplicationDataVersion());
    var addressedPayload = mock(ApplicationDataPayload.class);
    when(addressedPayload.laaReference()).thenReturn("LAA-SECOND");
    when(applicationDataStore.getAll(List.of(addressedDataId)))
        .thenReturn(Map.of(addressedDataId, addressedPayload));
    var command =
        new AssignLinkedGroupWorkItemCommand(
            GROUP_ID, SECOND_ID, CASEWORKER_ID, 0L, "{}", "Assigned", OCCURRED_AT);

    var result =
        handler.handle(command, group, stateManager, context, applicationDataStore, eventAppender);

    verify(eventAppender)
        .append(
            List.of(
                new WorkItemAssigned(
                    SECOND_ID, WorkItemType.APPLICATION, 1L, 1L, CASEWORKER_ID, OCCURRED_AT)));
    assertThat(result.items())
        .containsExactly(new AssignedWorkItems.AssignedItem(SECOND_ID, "LAA-SECOND"));
  }

  @Test
  void givenStaleVersion_whenAssign_thenConflictAndNoEvents() {
    givenExistingGroup();
    givenApplication(FIRST_ID, stateOf(activeUnassigned(FIRST_ID)));
    givenApplication(SECOND_ID, stateOf(activeUnassigned(SECOND_ID)));
    var command =
        new AssignLinkedGroupWorkItemCommand(
            GROUP_ID, FIRST_ID, CASEWORKER_ID, 1L, "{}", "Assigned", OCCURRED_AT);

    assertThatThrownBy(
            () ->
                handler.handle(
                    command, group, stateManager, context, applicationDataStore, eventAppender))
        .isInstanceOf(WorkItemAssignmentConflictException.class)
        .hasMessageContaining("the assignment version is stale");

    verifyNoInteractions(eventAppender);
  }

  @Test
  void givenMissingGroup_whenAssign_thenMembershipConflictAndNoEvents() {
    var command =
        new AssignLinkedGroupWorkItemCommand(
            GROUP_ID, FIRST_ID, CASEWORKER_ID, 0L, "{}", "Assigned", OCCURRED_AT);

    assertThatThrownBy(
            () ->
                handler.handle(
                    command, null, stateManager, context, applicationDataStore, eventAppender))
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

  @Test
  void givenAddressedItemNotInGroup_whenAssign_thenMembershipConflictBeforeLoadingMembers() {
    givenExistingGroup();
    var outsiderId = UUID.fromString("00000000-0000-0000-0000-000000000009");
    var command =
        new AssignLinkedGroupWorkItemCommand(
            GROUP_ID, outsiderId, CASEWORKER_ID, 0L, "{}", "Assigned", OCCURRED_AT);

    assertThatThrownBy(
            () ->
                handler.handle(
                    command, group, stateManager, context, applicationDataStore, eventAppender))
        .isInstanceOf(ApplicationLinkConflictException.class)
        .hasMessageContaining("membership of application " + outsiderId + " changed");

    verifyNoInteractions(stateManager, eventAppender);
  }

  @Test
  void givenAddressedItemNotInGroup_whenUnassign_thenMembershipConflictBeforeLoadingMembers() {
    givenExistingGroup();
    var outsiderId = UUID.fromString("00000000-0000-0000-0000-000000000009");
    var command =
        new UnassignLinkedGroupWorkItemCommand(
            GROUP_ID, outsiderId, 1L, "{}", "Unassigned", OCCURRED_AT);

    assertThatThrownBy(() -> handler.handle(command, group, stateManager, context, eventAppender))
        .isInstanceOf(ApplicationLinkConflictException.class)
        .hasMessageContaining("membership of application " + outsiderId + " changed");

    verifyNoInteractions(stateManager, eventAppender);
  }

  private void givenApplication(UUID applicationId, ApplicationState state) {
    var aggregate = mock(ApplicationAggregate.class);
    when(aggregate.getApplicationId()).thenReturn(applicationId);
    when(aggregate.getState()).thenReturn(state);
    when(stateManager.loadEntity(ApplicationAggregate.class, applicationId, context))
        .thenReturn(CompletableFuture.completedFuture(aggregate));
  }
}
