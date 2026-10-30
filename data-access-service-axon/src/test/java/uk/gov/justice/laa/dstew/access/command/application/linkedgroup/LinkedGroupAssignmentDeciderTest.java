package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationState;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationStateEvents;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationLinkConflictException;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssignmentConflictException;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemUnassigned;
import uk.gov.justice.laa.dstew.access.command.worklist.assign.AssignLinkedGroupWorkItemCommand;
import uk.gov.justice.laa.dstew.access.command.worklist.unassign.UnassignLinkedGroupWorkItemCommand;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;

class LinkedGroupAssignmentDeciderTest {
  private static final Instant OCCURRED_AT = Instant.parse("2026-10-27T12:00:00Z");
  private static final UUID CASEWORKER = UUID.fromString("00000000-0000-0000-0000-000000000101");
  private static final UUID OTHER_CASEWORKER =
      UUID.fromString("00000000-0000-0000-0000-000000000102");

  @Test
  void givenNoMembers_whenGroupAssignment_thenReturnsNone() {
    assertThat(LinkedGroupAssignmentDecider.groupAssignment(UUID.randomUUID(), List.of()))
        .isEqualTo(new GroupAssignment.None());
  }

  @Test
  void givenOnlyInactiveMembers_whenGroupAssignment_thenReturnsNone() {
    var autoGranted = state(ApplicationStateEvents.autoGranted(UUID.randomUUID()));
    var decided =
        state(ApplicationStateEvents.decidedAssigned(UUID.randomUUID(), OTHER_CASEWORKER));
    var notReady = state(ApplicationStateEvents.notReady(UUID.randomUUID()));

    assertThat(
            LinkedGroupAssignmentDecider.groupAssignment(
                UUID.randomUUID(), List.of(autoGranted, decided, notReady)))
        .isEqualTo(new GroupAssignment.None());
  }

  @Test
  void givenUnassignedMembers_whenGroupAssignment_thenReturnsUnassigned() {
    var first = state(ApplicationStateEvents.activeUnassigned(UUID.randomUUID()));
    var second = state(ApplicationStateEvents.activeUnassigned(UUID.randomUUID()));

    assertThat(
            LinkedGroupAssignmentDecider.groupAssignment(UUID.randomUUID(), List.of(first, second)))
        .isEqualTo(new GroupAssignment.Unassigned());
  }

  @Test
  void givenSameCaseworker_whenGroupAssignment_thenReturnsAssigned() {
    var first = state(ApplicationStateEvents.activeAssigned(UUID.randomUUID(), CASEWORKER));
    var second = state(ApplicationStateEvents.activeAssigned(UUID.randomUUID(), CASEWORKER));

    assertThat(
            LinkedGroupAssignmentDecider.groupAssignment(UUID.randomUUID(), List.of(first, second)))
        .isEqualTo(new GroupAssignment.Assigned(CASEWORKER));
  }

  @Test
  void givenInactiveDifferentCaseworker_whenGroupAssignment_thenUsesActiveAssignment() {
    var inactive =
        state(ApplicationStateEvents.decidedAssigned(UUID.randomUUID(), OTHER_CASEWORKER));
    var active = state(ApplicationStateEvents.activeAssigned(UUID.randomUUID(), CASEWORKER));

    assertThat(
            LinkedGroupAssignmentDecider.groupAssignment(
                UUID.randomUUID(), List.of(inactive, active)))
        .isEqualTo(new GroupAssignment.Assigned(CASEWORKER));
  }

  @Test
  void givenActiveMembersDisagree_whenGroupAssignment_thenThrowsConflict() {
    var first = state(ApplicationStateEvents.activeAssigned(UUID.randomUUID(), CASEWORKER));
    var second = state(ApplicationStateEvents.activeAssigned(UUID.randomUUID(), OTHER_CASEWORKER));

    assertThatThrownBy(
            () ->
                LinkedGroupAssignmentDecider.groupAssignment(
                    UUID.randomUUID(), List.of(first, second)))
        .isInstanceOf(ApplicationLinkConflictException.class)
        .hasMessageContaining("members do not share one assignment");
  }

  @Test
  void givenAssignedAndUnassignedActiveMembers_whenGroupAssignment_thenThrowsConflict() {
    var first = state(ApplicationStateEvents.activeAssigned(UUID.randomUUID(), CASEWORKER));
    var second = state(ApplicationStateEvents.activeUnassigned(UUID.randomUUID()));

    assertThatThrownBy(
            () ->
                LinkedGroupAssignmentDecider.groupAssignment(
                    UUID.randomUUID(), List.of(first, second)))
        .isInstanceOf(ApplicationLinkConflictException.class);
  }

  @Test
  void givenAssignedGroup_whenJoiningUnassigned_thenAssignsJoiningMember() {
    var lead = state(ApplicationStateEvents.activeAssigned(UUID.randomUUID(), CASEWORKER));
    var joining = state(ApplicationStateEvents.activeUnassigned(UUID.randomUUID()));

    var events =
        LinkedGroupAssignmentDecider.decideJoin(
            UUID.randomUUID(), List.of(lead), List.of(joining), OCCURRED_AT);

    assertThat(events).containsExactly(assigned(joining.getApplicationId(), 1L, 1L, CASEWORKER));
  }

  @Test
  void givenAssignedGroup_whenJoiningOtherCaseworker_thenOverwritesJoiningAssignment() {
    var lead = state(ApplicationStateEvents.activeAssigned(UUID.randomUUID(), CASEWORKER));
    var joining = state(ApplicationStateEvents.activeAssigned(UUID.randomUUID(), OTHER_CASEWORKER));

    var events =
        LinkedGroupAssignmentDecider.decideJoin(
            UUID.randomUUID(), List.of(lead), List.of(joining), OCCURRED_AT);

    assertThat(events).containsExactly(assigned(joining.getApplicationId(), 1L, 2L, CASEWORKER));
  }

  @Test
  void givenAlreadyMatchingAssignment_whenJoining_thenReturnsNoEvents() {
    var lead = state(ApplicationStateEvents.activeAssigned(UUID.randomUUID(), CASEWORKER));
    var joining = state(ApplicationStateEvents.activeAssigned(UUID.randomUUID(), CASEWORKER));

    assertThat(
            LinkedGroupAssignmentDecider.decideJoin(
                UUID.randomUUID(), List.of(lead), List.of(joining), OCCURRED_AT))
        .isEmpty();
  }

  @Test
  void givenUnassignedGroupAndAssignedJoiner_whenJoining_thenAssignsEveryActiveMemberInOrder() {
    var leadId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    var otherMemberId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    var joiningId = UUID.fromString("00000000-0000-0000-0000-000000000003");
    var lead = state(ApplicationStateEvents.activeUnassigned(leadId));
    var otherMember = state(ApplicationStateEvents.activeUnassigned(otherMemberId));
    var joining = state(ApplicationStateEvents.activeAssigned(joiningId, CASEWORKER));

    var events =
        LinkedGroupAssignmentDecider.decideJoin(
            UUID.randomUUID(), List.of(lead, otherMember), List.of(joining), OCCURRED_AT);

    assertThat(events)
        .containsExactly(
            assigned(leadId, 1L, 1L, CASEWORKER), assigned(otherMemberId, 1L, 1L, CASEWORKER));
  }

  @Test
  void givenNoAssignmentSource_whenJoining_thenReturnsNoEvents() {
    var lead = state(ApplicationStateEvents.activeUnassigned(UUID.randomUUID()));
    var joining = state(ApplicationStateEvents.activeUnassigned(UUID.randomUUID()));

    assertThat(
            LinkedGroupAssignmentDecider.decideJoin(
                UUID.randomUUID(), List.of(lead), List.of(joining), OCCURRED_AT))
        .isEmpty();
  }

  @Test
  void givenInactiveJoiner_whenJoiningAssignedGroup_thenDoesNotChangeInactiveApplication() {
    var lead = state(ApplicationStateEvents.activeAssigned(UUID.randomUUID(), CASEWORKER));
    var joining = state(ApplicationStateEvents.notReady(UUID.randomUUID()));

    assertThat(
            LinkedGroupAssignmentDecider.decideJoin(
                UUID.randomUUID(), List.of(lead), List.of(joining), OCCURRED_AT))
        .isEmpty();
  }

  @Test
  void givenAutoGrantedLead_whenAssignedApplicationJoins_thenDoesNotAssignInactiveLead() {
    var lead = state(ApplicationStateEvents.autoGranted(UUID.randomUUID()));
    var joining = state(ApplicationStateEvents.activeAssigned(UUID.randomUUID(), CASEWORKER));

    assertThat(
            LinkedGroupAssignmentDecider.decideJoin(
                UUID.randomUUID(), List.of(lead), List.of(joining), OCCURRED_AT))
        .doesNotContain(assigned(lead.getApplicationId(), 1L, 1L, CASEWORKER));
  }

  @Test
  void givenResubmittedJoiner_whenJoiningUnassignedGroup_thenIgnoresStaleAssignment() {
    var lead = state(ApplicationStateEvents.activeUnassigned(UUID.randomUUID()));
    var joining =
        state(ApplicationStateEvents.resubmittedAfterAssignment(UUID.randomUUID(), CASEWORKER));

    assertThat(
            LinkedGroupAssignmentDecider.decideJoin(
                UUID.randomUUID(), List.of(lead), List.of(joining), OCCURRED_AT))
        .isEmpty();
  }

  @Test
  void givenDisagreeingExistingMembers_whenJoining_thenThrowsConflict() {
    var first = state(ApplicationStateEvents.activeAssigned(UUID.randomUUID(), CASEWORKER));
    var second = state(ApplicationStateEvents.activeAssigned(UUID.randomUUID(), OTHER_CASEWORKER));

    assertThatThrownBy(
            () ->
                LinkedGroupAssignmentDecider.decideJoin(
                    UUID.randomUUID(), List.of(first, second), List.of(), OCCURRED_AT))
        .isInstanceOf(ApplicationLinkConflictException.class);
  }

  @Test
  void givenUnassignedGroup_whenAssigning_thenAddressesFirstThenOtherMembers() {
    var firstId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    var addressedId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    var group =
        LinkedApplicationGroupStates.group(UUID.randomUUID(), List.of(firstId, addressedId));
    var first = state(ApplicationStateEvents.activeUnassigned(firstId));
    var addressed = state(ApplicationStateEvents.activeUnassigned(addressedId));
    var command = assignCommand(group.getGroupId(), addressedId, 0L);

    var events =
        LinkedGroupAssignmentDecider.decideGroupAssign(
            group, Map.of(firstId, first, addressedId, addressed), command);

    assertThat(events)
        .containsExactly(
            assigned(addressedId, 1L, 1L, CASEWORKER), assigned(firstId, 1L, 1L, CASEWORKER));
  }

  @Test
  void givenInactiveMembers_whenAssigning_thenSkipsNotReadyAndDecidedMembers() {
    var addressedId = UUID.randomUUID();
    var notReadyId = UUID.randomUUID();
    var decidedId = UUID.randomUUID();
    var group =
        LinkedApplicationGroupStates.group(
            UUID.randomUUID(), List.of(addressedId, notReadyId, decidedId));
    var addressed = state(ApplicationStateEvents.activeUnassigned(addressedId));
    var notReady = state(ApplicationStateEvents.notReady(notReadyId));
    var decided = state(ApplicationStateEvents.decidedAssigned(decidedId, OTHER_CASEWORKER));

    var events =
        LinkedGroupAssignmentDecider.decideGroupAssign(
            group,
            Map.of(addressedId, addressed, notReadyId, notReady, decidedId, decided),
            assignCommand(group.getGroupId(), addressedId, 0L));

    assertThat(events).containsExactly(assigned(addressedId, 1L, 1L, CASEWORKER));
  }

  @Test
  void givenStaleVersion_whenAssigning_thenThrowsConflict() {
    var id = UUID.randomUUID();
    var group = LinkedApplicationGroupStates.group(UUID.randomUUID(), List.of(id));
    var state = state(ApplicationStateEvents.activeUnassigned(id));

    assertThatThrownBy(
            () ->
                LinkedGroupAssignmentDecider.decideGroupAssign(
                    group, Map.of(id, state), assignCommand(group.getGroupId(), id, 1L)))
        .isInstanceOf(WorkItemAssignmentConflictException.class)
        .hasMessageContaining("the assignment version is stale");
  }

  @Test
  void givenAssignedAddressedItem_whenAssigning_thenThrowsAlreadyAssignedConflict() {
    var id = UUID.randomUUID();
    var group = LinkedApplicationGroupStates.group(UUID.randomUUID(), List.of(id));
    var state = state(ApplicationStateEvents.activeAssigned(id, CASEWORKER));

    assertThatThrownBy(
            () ->
                LinkedGroupAssignmentDecider.decideGroupAssign(
                    group, Map.of(id, state), assignCommand(group.getGroupId(), id, 1L)))
        .isInstanceOf(WorkItemAssignmentConflictException.class)
        .hasMessageContaining("it is already assigned");
  }

  @Test
  void givenInactiveAddressedItem_whenAssigning_thenThrowsNotFound() {
    var id = UUID.randomUUID();
    var group = LinkedApplicationGroupStates.group(UUID.randomUUID(), List.of(id));
    var state = state(ApplicationStateEvents.notReady(id));

    assertThatThrownBy(
            () ->
                LinkedGroupAssignmentDecider.decideGroupAssign(
                    group, Map.of(id, state), assignCommand(group.getGroupId(), id, 0L)))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("Application work item is not active: " + id);
  }

  @Test
  void givenOtherMemberAssignedToDifferentCaseworker_whenAssigning_thenReportsLowestId() {
    var addressedId = UUID.fromString("00000000-0000-0000-0000-000000000003");
    var lowerOtherId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    var higherOtherId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    var group =
        LinkedApplicationGroupStates.group(
            UUID.randomUUID(), List.of(addressedId, lowerOtherId, higherOtherId));
    var members =
        Map.of(
            addressedId, state(ApplicationStateEvents.activeUnassigned(addressedId)),
            lowerOtherId,
                state(ApplicationStateEvents.activeAssigned(lowerOtherId, OTHER_CASEWORKER)),
            higherOtherId,
                state(ApplicationStateEvents.activeAssigned(higherOtherId, OTHER_CASEWORKER)));

    assertThatThrownBy(
            () ->
                LinkedGroupAssignmentDecider.decideGroupAssign(
                    group, members, assignCommand(group.getGroupId(), addressedId, 0L)))
        .isInstanceOf(WorkItemAssignmentConflictException.class)
        .hasMessageContaining("Work item " + lowerOtherId + " cannot be updated");
  }

  @Test
  void givenAddressedItemOutsideGroup_whenAssigning_thenThrowsMembershipChanged() {
    var group = LinkedApplicationGroupStates.group(UUID.randomUUID(), List.of(UUID.randomUUID()));
    var missingId = UUID.randomUUID();

    assertThatThrownBy(
            () ->
                LinkedGroupAssignmentDecider.decideGroupAssign(
                    group, Map.of(), assignCommand(group.getGroupId(), missingId, 0L)))
        .isInstanceOf(ApplicationLinkConflictException.class)
        .hasMessageContaining("membership of application " + missingId + " changed");
  }

  @Test
  void givenDissolvedGroup_whenAssigning_thenThrowsMembershipChanged() {
    var id = UUID.randomUUID();
    var group = LinkedApplicationGroupStates.dissolved(UUID.randomUUID(), List.of(id));

    assertThatThrownBy(
            () ->
                LinkedGroupAssignmentDecider.decideGroupAssign(
                    group,
                    Map.of(id, state(ApplicationStateEvents.activeUnassigned(id))),
                    assignCommand(group.getGroupId(), id, 0L)))
        .isInstanceOf(ApplicationLinkConflictException.class);
  }

  @Test
  void givenAssignedGroup_whenUnassigning_thenClearsAllAssignedActiveMembers() {
    var firstId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    var addressedId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    var group =
        LinkedApplicationGroupStates.group(UUID.randomUUID(), List.of(firstId, addressedId));
    var first = state(ApplicationStateEvents.activeAssigned(firstId, OTHER_CASEWORKER));
    var addressed = state(ApplicationStateEvents.activeAssigned(addressedId, CASEWORKER));

    var events =
        LinkedGroupAssignmentDecider.decideGroupUnassign(
            group,
            Map.of(firstId, first, addressedId, addressed),
            new UnassignLinkedGroupWorkItemCommand(
                group.getGroupId(), addressedId, 1L, "{}", "Unassigned", OCCURRED_AT));

    assertThat(events)
        .containsExactly(unassigned(addressedId, 1L, 2L), unassigned(firstId, 1L, 2L));
  }

  @Test
  void givenUnassignedAddressedItem_whenUnassigning_thenThrowsConflict() {
    var id = UUID.randomUUID();
    var group = LinkedApplicationGroupStates.group(UUID.randomUUID(), List.of(id));

    assertThatThrownBy(
            () ->
                LinkedGroupAssignmentDecider.decideGroupUnassign(
                    group,
                    Map.of(id, state(ApplicationStateEvents.activeUnassigned(id))),
                    new UnassignLinkedGroupWorkItemCommand(
                        group.getGroupId(), id, 0L, "{}", "Unassigned", OCCURRED_AT)))
        .isInstanceOf(WorkItemAssignmentConflictException.class)
        .hasMessageContaining("it is already unassigned");
  }

  @Test
  void givenStaleVersion_whenUnassigning_thenThrowsConflict() {
    var id = UUID.randomUUID();
    var group = LinkedApplicationGroupStates.group(UUID.randomUUID(), List.of(id));

    assertThatThrownBy(
            () ->
                LinkedGroupAssignmentDecider.decideGroupUnassign(
                    group,
                    Map.of(id, state(ApplicationStateEvents.activeAssigned(id, CASEWORKER))),
                    new UnassignLinkedGroupWorkItemCommand(
                        group.getGroupId(), id, 0L, "{}", "Unassigned", OCCURRED_AT)))
        .isInstanceOf(WorkItemAssignmentConflictException.class)
        .hasMessageContaining("the assignment version is stale");
  }

  @Test
  void givenOtherMembersAssigned_whenReady_thenTakesTheirAssignment() {
    var id = UUID.randomUUID();
    var otherId = UUID.randomUUID();
    var group = LinkedApplicationGroupStates.group(UUID.randomUUID(), List.of(id, otherId));
    var stateBeforeReady = state(ApplicationStateEvents.notReady(id));
    var other = state(ApplicationStateEvents.activeAssigned(otherId, CASEWORKER));

    assertThat(
            LinkedGroupAssignmentDecider.decideReadyAssignment(
                group, stateBeforeReady, 1L, List.of(other), OCCURRED_AT))
        .contains(assigned(id, 1L, 1L, CASEWORKER));
  }

  @Test
  void givenUnassignedOthersAndStaleAssignment_whenReady_thenClearsAssignment() {
    var id = UUID.randomUUID();
    var otherId = UUID.randomUUID();
    var group = LinkedApplicationGroupStates.group(UUID.randomUUID(), List.of(id, otherId));
    var stateBeforeReady = state(ApplicationStateEvents.resubmittedAfterAssignment(id, CASEWORKER));
    var other = state(ApplicationStateEvents.activeUnassigned(otherId));

    assertThat(
            LinkedGroupAssignmentDecider.decideReadyAssignment(
                group, stateBeforeReady, 3L, List.of(other), OCCURRED_AT))
        .contains(unassigned(id, 3L, 2L));
  }

  @Test
  void givenOthersAssignedToNewCaseworker_whenReady_thenReplacesStaleAssignment() {
    var id = UUID.randomUUID();
    var otherId = UUID.randomUUID();
    var group = LinkedApplicationGroupStates.group(UUID.randomUUID(), List.of(id, otherId));
    var stateBeforeReady =
        state(ApplicationStateEvents.resubmittedAfterAssignment(id, OTHER_CASEWORKER));
    var other = state(ApplicationStateEvents.activeAssigned(otherId, CASEWORKER));

    assertThat(
            LinkedGroupAssignmentDecider.decideReadyAssignment(
                group, stateBeforeReady, 3L, List.of(other), OCCURRED_AT))
        .contains(assigned(id, 3L, 2L, CASEWORKER));
  }

  @Test
  void givenNoOtherActiveMembers_whenReady_thenKeepsCurrentAssignment() {
    var id = UUID.randomUUID();
    var inactiveId = UUID.randomUUID();
    var group = LinkedApplicationGroupStates.group(UUID.randomUUID(), List.of(id, inactiveId));

    assertThat(
            LinkedGroupAssignmentDecider.decideReadyAssignment(
                group,
                state(ApplicationStateEvents.notReady(id)),
                1L,
                List.of(state(ApplicationStateEvents.notReady(inactiveId))),
                OCCURRED_AT))
        .isEmpty();
  }

  @Test
  void givenApplicationOutsideGroup_whenReady_thenThrowsMembershipChanged() {
    var id = UUID.randomUUID();
    var group = LinkedApplicationGroupStates.group(UUID.randomUUID(), List.of(UUID.randomUUID()));

    assertThatThrownBy(
            () ->
                LinkedGroupAssignmentDecider.decideReadyAssignment(
                    group, state(ApplicationStateEvents.notReady(id)), 1L, List.of(), OCCURRED_AT))
        .isInstanceOf(ApplicationLinkConflictException.class);
  }

  private static ApplicationState state(List<Object> events) {
    return ApplicationStateEvents.stateOf(events);
  }

  private static AssignLinkedGroupWorkItemCommand assignCommand(
      UUID groupId, UUID workItemId, long version) {
    return new AssignLinkedGroupWorkItemCommand(
        groupId, workItemId, CASEWORKER, version, "{}", "Assigned", OCCURRED_AT);
  }

  private static WorkItemAssigned assigned(
      UUID id, long itemVersion, long assignmentVersion, UUID caseworkerId) {
    return new WorkItemAssigned(
        id, WorkItemType.APPLICATION, itemVersion, assignmentVersion, caseworkerId, OCCURRED_AT);
  }

  private static WorkItemUnassigned unassigned(UUID id, long itemVersion, long assignmentVersion) {
    return new WorkItemUnassigned(
        id, WorkItemType.APPLICATION, itemVersion, assignmentVersion, OCCURRED_AT);
  }
}
