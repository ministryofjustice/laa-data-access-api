package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
  private static final UUID GROUP_ID = UUID.fromString("00000000-0000-0000-0000-000000000100");
  private static final UUID LOWER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID MIDDLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final UUID ADDRESSED_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

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

  // ── group assign: rules ────────────────────────────────────────────────

  @ParameterizedTest(name = "{0}")
  @MethodSource("rejectedGroupAssigns")
  void givenRejectedRequest_whenValidateGroupAssign_thenThrows(
      String description,
      SortedMap<UUID, ApplicationState> members,
      long expectedVersion,
      Class<? extends RuntimeException> exceptionType,
      String message) {
    assertThatThrownBy(
            () ->
                LinkedGroupAssignmentDecider.validateGroupAssign(
                    members, assignCommand(expectedVersion)))
        .isInstanceOf(exceptionType)
        .hasMessageContaining(message);
  }

  private static Stream<Arguments> rejectedGroupAssigns() {
    return Stream.of(
        Arguments.of(
            "stale version",
            members(ApplicationStateEvents.activeUnassigned(ADDRESSED_ID)),
            1L,
            WorkItemAssignmentConflictException.class,
            "the assignment version is stale"),
        Arguments.of(
            "addressed item already assigned",
            members(ApplicationStateEvents.activeAssigned(ADDRESSED_ID, CASEWORKER)),
            1L,
            WorkItemAssignmentConflictException.class,
            "it is already assigned"),
        Arguments.of(
            "addressed item not active",
            members(ApplicationStateEvents.notReady(ADDRESSED_ID)),
            0L,
            ResourceNotFoundException.class,
            "Application work item is not active: " + ADDRESSED_ID),
        Arguments.of(
            "addressed item missing from members",
            members(ApplicationStateEvents.activeUnassigned(LOWER_ID)),
            0L,
            ResourceNotFoundException.class,
            "No application work item found with id: " + ADDRESSED_ID),
        Arguments.of(
            "other active member assigned elsewhere reports lowest id",
            members(
                ApplicationStateEvents.activeUnassigned(ADDRESSED_ID),
                ApplicationStateEvents.activeAssigned(LOWER_ID, OTHER_CASEWORKER),
                ApplicationStateEvents.activeAssigned(MIDDLE_ID, OTHER_CASEWORKER)),
            0L,
            WorkItemAssignmentConflictException.class,
            "Work item " + LOWER_ID + " cannot be updated"));
  }

  @Test
  void givenOtherMemberAlreadyWithCaseworker_whenValidateGroupAssign_thenAccepts() {
    var members =
        members(
            ApplicationStateEvents.activeUnassigned(ADDRESSED_ID),
            ApplicationStateEvents.activeAssigned(LOWER_ID, CASEWORKER));

    assertThatCode(
            () -> LinkedGroupAssignmentDecider.validateGroupAssign(members, assignCommand(0L)))
        .doesNotThrowAnyException();
  }

  @Test
  void givenInactiveMemberAssignedElsewhere_whenValidateGroupAssign_thenAccepts() {
    var members =
        members(
            ApplicationStateEvents.activeUnassigned(ADDRESSED_ID),
            ApplicationStateEvents.decidedAssigned(LOWER_ID, OTHER_CASEWORKER));

    assertThatCode(
            () -> LinkedGroupAssignmentDecider.validateGroupAssign(members, assignCommand(0L)))
        .doesNotThrowAnyException();
  }

  // ── group assign: effects ──────────────────────────────────────────────

  @Test
  void givenUnassignedGroup_whenDecideGroupAssign_thenAddressesFirstThenOtherMembers() {
    var members =
        members(
            ApplicationStateEvents.activeUnassigned(LOWER_ID),
            ApplicationStateEvents.activeUnassigned(ADDRESSED_ID));

    assertThat(LinkedGroupAssignmentDecider.decideGroupAssign(members, assignCommand(0L)))
        .containsExactly(
            assigned(ADDRESSED_ID, 1L, 1L, CASEWORKER), assigned(LOWER_ID, 1L, 1L, CASEWORKER));
  }

  @Test
  void givenInactiveMembers_whenDecideGroupAssign_thenSkipsNotReadyAndDecidedMembers() {
    var members =
        members(
            ApplicationStateEvents.activeUnassigned(ADDRESSED_ID),
            ApplicationStateEvents.notReady(LOWER_ID),
            ApplicationStateEvents.decidedAssigned(MIDDLE_ID, OTHER_CASEWORKER));

    assertThat(LinkedGroupAssignmentDecider.decideGroupAssign(members, assignCommand(0L)))
        .containsExactly(assigned(ADDRESSED_ID, 1L, 1L, CASEWORKER));
  }

  // ── group unassign: rules ──────────────────────────────────────────────

  @ParameterizedTest(name = "{0}")
  @MethodSource("rejectedGroupUnassigns")
  void givenRejectedRequest_whenValidateGroupUnassign_thenThrows(
      String description, SortedMap<UUID, ApplicationState> members, String message) {
    assertThatThrownBy(
            () -> LinkedGroupAssignmentDecider.validateGroupUnassign(members, unassignCommand(0L)))
        .isInstanceOf(WorkItemAssignmentConflictException.class)
        .hasMessageContaining(message);
  }

  private static Stream<Arguments> rejectedGroupUnassigns() {
    return Stream.of(
        Arguments.of(
            "addressed item already unassigned",
            members(ApplicationStateEvents.activeUnassigned(ADDRESSED_ID)),
            "it is already unassigned"),
        Arguments.of(
            "stale version",
            members(ApplicationStateEvents.activeAssigned(ADDRESSED_ID, CASEWORKER)),
            "the assignment version is stale"));
  }

  @Test
  void givenAssignedAddressedItem_whenValidateGroupUnassign_thenAccepts() {
    var members = members(ApplicationStateEvents.activeAssigned(ADDRESSED_ID, CASEWORKER));

    assertThatCode(
            () -> LinkedGroupAssignmentDecider.validateGroupUnassign(members, unassignCommand(1L)))
        .doesNotThrowAnyException();
  }

  // ── group unassign: effects ────────────────────────────────────────────

  @Test
  void givenAssignedGroup_whenDecideGroupUnassign_thenClearsAddressedFirstThenOthers() {
    var members =
        members(
            ApplicationStateEvents.activeAssigned(LOWER_ID, OTHER_CASEWORKER),
            ApplicationStateEvents.activeAssigned(ADDRESSED_ID, CASEWORKER));

    assertThat(LinkedGroupAssignmentDecider.decideGroupUnassign(members, unassignCommand(1L)))
        .containsExactly(unassigned(ADDRESSED_ID, 1L, 2L), unassigned(LOWER_ID, 1L, 2L));
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

  @SafeVarargs
  private static SortedMap<UUID, ApplicationState> members(List<Object>... eventStreams) {
    var members = new TreeMap<UUID, ApplicationState>();
    for (var events : eventStreams) {
      var state = ApplicationStateEvents.stateOf(events);
      members.put(state.getApplicationId(), state);
    }
    return members;
  }

  private static AssignLinkedGroupWorkItemCommand assignCommand(long expectedVersion) {
    return new AssignLinkedGroupWorkItemCommand(
        GROUP_ID, ADDRESSED_ID, CASEWORKER, expectedVersion, "{}", "Assigned", OCCURRED_AT);
  }

  private static UnassignLinkedGroupWorkItemCommand unassignCommand(long expectedVersion) {
    return new UnassignLinkedGroupWorkItemCommand(
        GROUP_ID, ADDRESSED_ID, expectedVersion, "{}", "Unassigned", OCCURRED_AT);
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
