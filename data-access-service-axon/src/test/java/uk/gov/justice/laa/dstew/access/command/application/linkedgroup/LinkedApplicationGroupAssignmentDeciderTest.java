package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.AssignLinkedGroupCommand;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.DelegatedAssignment;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.InheritLinkedGroupAssignmentCommand;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.LinkedGroupAssignedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.LinkedGroupMemberAssignmentInheritedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.LinkedGroupUnassignedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.UnassignLinkedGroupCommand;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.ValidateLinkedGroupAssigneeCommand;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationLinkConflictException;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssignmentConflictException;

@ExtendWith(MockitoExtension.class)
class LinkedApplicationGroupAssignmentDeciderTest {

  private static final UUID GROUP_ID = UUID.randomUUID();
  private static final UUID LEAD_ID = UUID.randomUUID();
  private static final UUID MEMBER_ID = UUID.randomUUID();
  private static final UUID NEW_MEMBER_ID = UUID.randomUUID();
  private static final UUID C1 = UUID.randomUUID();
  private static final UUID C2 = UUID.randomUUID();
  private static final Instant AT = Instant.parse("2026-10-22T09:00:00Z");

  @ParameterizedTest(name = "{0}")
  @MethodSource("joiningRules")
  void givenJoiningCaseworkers_whenResolved_thenLeadSideWins(
      String rule, UUID leadSide, UUID joining, UUID expected) {
    assertThat(LinkedApplicationGroupDecider.resolveGroupCaseworker(leadSide, joining))
        .as(rule)
        .isEqualTo(expected);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("joiningRules")
  void givenNewGroup_whenEstablished_thenAssignsOnlyWhenNeeded(
      String rule, UUID leadCaseworker, UUID memberCaseworker, UUID expected) {
    var members = List.of(LEAD_ID, MEMBER_ID);
    var assignments =
        Map.of(
            LEAD_ID, new DelegatedAssignment(leadCaseworker),
            MEMBER_ID, new DelegatedAssignment(memberCaseworker));
    var events =
        LinkedApplicationGroupDecider.decideEstablish(
            new LinkedApplicationGroupState(),
            new EstablishLinkedApplicationGroupCommand(
                GROUP_ID, LEAD_ID, members, assignments, AT));

    assertThat(events.getFirst())
        .isEqualTo(new LinkedApplicationGroupCreatedEvent(GROUP_ID, LEAD_ID, members, AT));
    if (expected == null) {
      assertThat(events).hasSize(1);
    } else {
      assertThat(events)
          .containsExactly(
              new LinkedApplicationGroupCreatedEvent(GROUP_ID, LEAD_ID, members, AT),
              new LinkedGroupAssignedEvent(GROUP_ID, expected, 1, AT));
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("addRules")
  void givenGroupAssignment_whenMemberAdded_thenAppliesJoiningRules(
      String rule, UUID groupCaseworker, UUID memberCaseworker, UUID expectedEventCaseworker) {
    var state = group(LEAD_ID, List.of(LEAD_ID, MEMBER_ID), groupCaseworker, 5);
    var events =
        LinkedApplicationGroupDecider.decideAddApplication(
            state,
            new AddApplicationToLinkedGroupCommand(
                GROUP_ID, NEW_MEMBER_ID, 0, new DelegatedAssignment(memberCaseworker), AT));

    assertThat(events.getFirst())
        .isEqualTo(new MemberAddedToGroupEvent(GROUP_ID, LEAD_ID, NEW_MEMBER_ID, AT));
    if (expectedEventCaseworker == null) {
      assertThat(events).hasSize(1);
    } else {
      assertThat(events)
          .containsExactly(
              new MemberAddedToGroupEvent(GROUP_ID, LEAD_ID, NEW_MEMBER_ID, AT),
              new LinkedGroupAssignedEvent(GROUP_ID, expectedEventCaseworker, 6, AT));
    }
  }

  @Test
  void givenExistingMember_whenAddedAgain_thenAppendsNothing() {
    assertThat(
            LinkedApplicationGroupDecider.decideAddApplication(
                group(LEAD_ID, List.of(LEAD_ID, MEMBER_ID), C1, 5),
                new AddApplicationToLinkedGroupCommand(
                    GROUP_ID, MEMBER_ID, 0, new DelegatedAssignment(C2), AT)))
        .isEmpty();
  }

  @Test
  void givenUnassignedGroup_whenAssigned_thenAppendsGroupAssignment() {
    assertThat(
            LinkedApplicationGroupDecider.decideAssign(
                group(LEAD_ID, List.of(LEAD_ID, MEMBER_ID), null, 1),
                new AssignLinkedGroupCommand(GROUP_ID, MEMBER_ID, C1, 1, AT)))
        .isEqualTo(new LinkedGroupAssignedEvent(GROUP_ID, C1, 2, AT));
  }

  @Test
  void givenAssignedGroup_whenAssigned_thenRejectsAsAlreadyAssigned() {
    assertThatThrownBy(
            () ->
                LinkedApplicationGroupDecider.decideAssign(
                    group(LEAD_ID, List.of(LEAD_ID, MEMBER_ID), C1, 1),
                    new AssignLinkedGroupCommand(GROUP_ID, MEMBER_ID, C2, 1, AT)))
        .isInstanceOf(WorkItemAssignmentConflictException.class)
        .hasMessage("Work item " + MEMBER_ID + " cannot be updated: it is already assigned");
  }

  @ParameterizedTest
  @ValueSource(longs = {0L, 2L})
  void givenStaleVersion_whenAssigned_thenRejectsAsStale(long expectedVersion) {
    assertThatThrownBy(
            () ->
                LinkedApplicationGroupDecider.decideAssign(
                    group(LEAD_ID, List.of(LEAD_ID, MEMBER_ID), null, 1),
                    new AssignLinkedGroupCommand(GROUP_ID, MEMBER_ID, C1, expectedVersion, AT)))
        .isInstanceOf(WorkItemAssignmentConflictException.class)
        .hasMessageEndingWith("the assignment version is stale");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("nonMemberStates")
  void givenNonMember_whenAssigned_thenRejectsAsNotMember(
      String scenario, LinkedApplicationGroupState state) {
    assertThatThrownBy(
            () ->
                LinkedApplicationGroupDecider.decideAssign(
                    state, new AssignLinkedGroupCommand(GROUP_ID, MEMBER_ID, C1, 0, AT)))
        .as(scenario)
        .isInstanceOf(WorkItemAssignmentConflictException.class)
        .hasMessageEndingWith("it is no longer a member of its linked group");
  }

  @Test
  void givenAssignedGroup_whenUnassigned_thenAppendsGroupUnassignment() {
    assertThat(
            LinkedApplicationGroupDecider.decideUnassign(
                group(LEAD_ID, List.of(LEAD_ID, MEMBER_ID), C1, 2),
                new UnassignLinkedGroupCommand(GROUP_ID, MEMBER_ID, 2, AT)))
        .isEqualTo(new LinkedGroupUnassignedEvent(GROUP_ID, 3, AT));
  }

  @Test
  void givenUnassignedGroup_whenUnassigned_thenRejectsAsAlreadyUnassigned() {
    assertThatThrownBy(
            () ->
                LinkedApplicationGroupDecider.decideUnassign(
                    group(LEAD_ID, List.of(LEAD_ID, MEMBER_ID), null, 2),
                    new UnassignLinkedGroupCommand(GROUP_ID, MEMBER_ID, 2, AT)))
        .isInstanceOf(WorkItemAssignmentConflictException.class)
        .hasMessage("Work item " + MEMBER_ID + " cannot be updated: it is already unassigned");
  }

  @Test
  void givenStaleVersion_whenUnassigned_thenRejectsAsStale() {
    assertThatThrownBy(
            () ->
                LinkedApplicationGroupDecider.decideUnassign(
                    group(LEAD_ID, List.of(LEAD_ID, MEMBER_ID), C1, 2),
                    new UnassignLinkedGroupCommand(GROUP_ID, MEMBER_ID, 1, AT)))
        .isInstanceOf(WorkItemAssignmentConflictException.class)
        .hasMessageEndingWith("the assignment version is stale");
  }

  @Test
  void givenAssignedGroup_whenMemberInherits_thenAppendsInheritedAssignment() {
    assertThat(
            LinkedApplicationGroupDecider.decideInherit(
                group(LEAD_ID, List.of(LEAD_ID, MEMBER_ID), C1, 4),
                new InheritLinkedGroupAssignmentCommand(GROUP_ID, MEMBER_ID, AT)))
        .isEqualTo(
            Optional.of(
                new LinkedGroupMemberAssignmentInheritedEvent(GROUP_ID, MEMBER_ID, C1, 4, AT)));
  }

  @Test
  void givenUnassignedGroup_whenMemberInherits_thenAppendsNothing() {
    assertThat(
            LinkedApplicationGroupDecider.decideInherit(
                group(LEAD_ID, List.of(LEAD_ID, MEMBER_ID), null, 4),
                new InheritLinkedGroupAssignmentCommand(GROUP_ID, MEMBER_ID, AT)))
        .isEmpty();
  }

  @Test
  void givenNonMember_whenMemberInherits_thenRejectsWithLinkConflict() {
    assertThatThrownBy(
            () ->
                LinkedApplicationGroupDecider.decideInherit(
                    group(LEAD_ID, List.of(LEAD_ID), C1, 4),
                    new InheritLinkedGroupAssignmentCommand(GROUP_ID, MEMBER_ID, AT)))
        .isInstanceOf(ApplicationLinkConflictException.class)
        .hasMessage(
            "Application "
                + MEMBER_ID
                + " is not a member of linked application group "
                + GROUP_ID);
  }

  @Test
  void givenMatchingAssignee_whenValidated_thenAccepts() {
    assertThatCode(
            () ->
                LinkedApplicationGroupDecider.validateAssignee(
                    group(LEAD_ID, List.of(LEAD_ID, MEMBER_ID), C1, 4),
                    new ValidateLinkedGroupAssigneeCommand(GROUP_ID, MEMBER_ID, C1)))
        .doesNotThrowAnyException();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidAssignees")
  void givenInvalidAssignee_whenValidated_thenRejects(
      String scenario, UUID groupCaseworker, UUID suppliedCaseworker, String reason) {
    assertThatThrownBy(
            () ->
                LinkedApplicationGroupDecider.validateAssignee(
                    group(LEAD_ID, List.of(LEAD_ID, MEMBER_ID), groupCaseworker, 4),
                    new ValidateLinkedGroupAssigneeCommand(
                        GROUP_ID, MEMBER_ID, suppliedCaseworker)))
        .as(scenario)
        .isInstanceOf(WorkItemAssignmentConflictException.class)
        .hasMessage("Work item " + MEMBER_ID + " cannot be updated: " + reason);
  }

  @Test
  void givenNonMember_whenValidated_thenRejectsAsNotMember() {
    assertThatThrownBy(
            () ->
                LinkedApplicationGroupDecider.validateAssignee(
                    group(LEAD_ID, List.of(LEAD_ID), C1, 4),
                    new ValidateLinkedGroupAssigneeCommand(GROUP_ID, MEMBER_ID, C1)))
        .isInstanceOf(WorkItemAssignmentConflictException.class)
        .hasMessageEndingWith("it is no longer a member of its linked group");
  }

  @Test
  void givenThreeMembers_whenMemberRemoved_thenEventCarriesGroupAssignment() {
    var state = group(LEAD_ID, List.of(LEAD_ID, MEMBER_ID, NEW_MEMBER_ID), C1, 6);
    var removal =
        LinkedApplicationGroupDecider.decideRemoveApplication(
            state, new RemoveApplicationFromLinkedGroupCommand(GROUP_ID, MEMBER_ID, 0, AT));

    assertThat(removal)
        .isEqualTo(new MemberRemovedFromGroupEvent(GROUP_ID, LEAD_ID, MEMBER_ID, 1, C1, 6, AT));
    assertThat(LinkedApplicationGroupDecider.removalResult(removal))
        .isEqualTo(new LinkedGroupRemovalResult(GROUP_ID, List.of(MEMBER_ID), C1, 6));
  }

  @Test
  void givenTwoMembers_whenMemberRemoved_thenDissolutionReleasesBoth() {
    var members = List.of(LEAD_ID, MEMBER_ID);
    var state = group(LEAD_ID, members, C1, 6);
    var removal =
        LinkedApplicationGroupDecider.decideRemoveApplication(
            state, new RemoveApplicationFromLinkedGroupCommand(GROUP_ID, MEMBER_ID, 0, AT));

    assertThat(removal)
        .isEqualTo(
            new LinkedApplicationGroupDissolvedEvent(
                GROUP_ID, LEAD_ID, MEMBER_ID, members, 1, C1, 6, AT));
    assertThat(LinkedApplicationGroupDecider.removalResult(removal))
        .isEqualTo(
            new LinkedGroupRemovalResult(GROUP_ID, members.stream().sorted().toList(), C1, 6));
  }

  private static LinkedApplicationGroupState group(
      UUID leadId, List<UUID> members, UUID caseworkerId, long assignmentVersion) {
    var state = new LinkedApplicationGroupState();
    state.groupId = GROUP_ID;
    state.leadApplicationId = leadId;
    state.memberApplicationIds = members;
    state.groupVersion = 0;
    state.dissolved = false;
    state.caseworkerId = caseworkerId;
    state.assignmentVersion = assignmentVersion;
    return state;
  }

  private static Stream<Arguments> joiningRules() {
    return Stream.of(
        Arguments.of("AC1", C1, null, C1),
        Arguments.of("AC2", C1, C2, C1),
        Arguments.of("AC3", null, C2, C2),
        Arguments.of("AC4 neither", null, null, null),
        Arguments.of("AC4 same", C1, C1, C1));
  }

  private static Stream<Arguments> addRules() {
    return Stream.of(
        Arguments.of("AC1", C1, null, null),
        Arguments.of("AC2", C1, C2, null),
        Arguments.of("AC3", null, C2, C2),
        Arguments.of("AC4 neither", null, null, null),
        Arguments.of("AC4 same", C1, C1, null));
  }

  private static Stream<Arguments> nonMemberStates() {
    var notEstablished = new LinkedApplicationGroupState();
    var dissolved = group(LEAD_ID, List.of(LEAD_ID), C1, 0);
    dissolved.dissolved = true;
    return Stream.of(
        Arguments.of("not established", notEstablished),
        Arguments.of("dissolved", dissolved),
        Arguments.of("not a member", group(LEAD_ID, List.of(LEAD_ID), C1, 0)));
  }

  private static Stream<Arguments> invalidAssignees() {
    return Stream.of(
        Arguments.of("unassigned", null, C1, "it is unassigned"),
        Arguments.of(
            "other caseworker", C1, C2, "the supplied caseworker is not its current assignee"));
  }
}
