package uk.gov.justice.laa.dstew.access.query.groupassignment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class LinkedGroupAssignmentTrackerTest {
  private static final UUID APPLICATION = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID MEMBER = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final UUID GROUP = UUID.fromString("00000000-0000-0000-0000-000000000010");
  private static final UUID CASEWORKER = UUID.fromString("00000000-0000-0000-0000-000000000020");
  private static final UUID OTHER_CASEWORKER =
      UUID.fromString("00000000-0000-0000-0000-000000000021");

  private LinkedGroupAssignmentTracker tracker;
  private InMemoryGroupAssignmentTrackingStore store;

  @BeforeEach
  void setUp() {
    tracker = new LinkedGroupAssignmentTracker();
    store = new InMemoryGroupAssignmentTrackingStore();
  }

  @Test
  void givenStandaloneApplication_whenReady_thenActiveAtEventVersion() {
    AssignmentChange change = tracker.applicationReady(store, APPLICATION, 4L);

    assertThat(change).isEqualTo(new AssignmentChange(APPLICATION, null, null, null, 4L));
    assertThat(store.findApplication(APPLICATION))
        .contains(new TrackedApplication(APPLICATION, null, true, null, 4L));
  }

  @Test
  void givenAssignedGroup_whenMemberBecomesReady_thenWaitsForInheritedAssignment() {
    tracker.groupCreated(store, GROUP, List.of(APPLICATION, MEMBER));
    tracker.groupAssignmentChanged(store, GROUP, CASEWORKER, 1L);

    AssignmentChange change = tracker.applicationReady(store, APPLICATION, 0L);

    assertThat(change).isEqualTo(new AssignmentChange(APPLICATION, GROUP, null, null, 1L));
    assertThat(store.findApplication(APPLICATION))
        .contains(new TrackedApplication(APPLICATION, GROUP, true, null, 1L));

    assertThat(tracker.memberInherited(store, GROUP, APPLICATION, CASEWORKER, 1L))
        .contains(new AssignmentChange(APPLICATION, GROUP, null, CASEWORKER, 1L));
  }

  @Test
  void givenReadyMembers_whenGroupCreated_thenAssignmentOwnerChanges() {
    tracker.applicationReady(store, APPLICATION, 3L);
    tracker.directAssignmentChanged(store, APPLICATION, CASEWORKER, 4L);

    List<AssignmentChange> changes = tracker.groupCreated(store, GROUP, List.of(APPLICATION));

    assertThat(changes)
        .containsExactly(new AssignmentChange(APPLICATION, GROUP, CASEWORKER, CASEWORKER, 0L));
  }

  @Test
  void givenUnassignedGroup_whenActiveMemberJoins_thenKeepsOwnAssignment() {
    tracker.groupCreated(store, GROUP, List.of(APPLICATION));
    tracker.applicationReady(store, MEMBER, 0L);
    tracker.directAssignmentChanged(store, MEMBER, CASEWORKER, 2L);

    List<AssignmentChange> changes = tracker.memberAdded(store, GROUP, MEMBER);

    assertThat(changes)
        .containsExactly(new AssignmentChange(MEMBER, GROUP, CASEWORKER, CASEWORKER, 0L));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("joinRules")
  void givenGroupAssignment_whenActiveMemberAdded_thenJoiningRuleApplies(
      String rule, UUID groupCaseworker, UUID memberCaseworker, UUID expectedShownCaseworker) {
    tracker.applicationReady(store, APPLICATION, 0L);
    tracker.groupCreated(store, GROUP, List.of(APPLICATION));
    if (groupCaseworker != null) {
      tracker.groupAssignmentChanged(store, GROUP, groupCaseworker, 1L);
    }
    tracker.applicationReady(store, MEMBER, 0L);
    if (memberCaseworker != null) {
      tracker.directAssignmentChanged(store, MEMBER, memberCaseworker, 1L);
    }

    List<AssignmentChange> changes = tracker.memberAdded(store, GROUP, MEMBER);

    assertThat(changes)
        .containsExactly(
            new AssignmentChange(
                MEMBER,
                GROUP,
                memberCaseworker,
                expectedShownCaseworker,
                groupCaseworker == null ? 0L : 1L));
    assertThat(store.findApplication(MEMBER))
        .contains(
            new TrackedApplication(
                MEMBER, GROUP, true, expectedShownCaseworker, groupCaseworker == null ? 0L : 1L));
  }

  private static Stream<Arguments> joinRules() {
    return Stream.of(
        Arguments.of("AC1: group assigned, member unassigned", CASEWORKER, null, CASEWORKER),
        Arguments.of(
            "AC2: group assigned, member assigned differently",
            CASEWORKER,
            OTHER_CASEWORKER,
            CASEWORKER),
        Arguments.of(
            "AC3: group unassigned, member assigned", null, OTHER_CASEWORKER, OTHER_CASEWORKER),
        Arguments.of("AC4: neither assigned", null, null, null));
  }

  @Test
  void givenAssignedGroup_whenActiveMemberJoins_thenTakesGroupAssignment() {
    tracker.groupCreated(store, GROUP, List.of(APPLICATION));
    tracker.groupAssignmentChanged(store, GROUP, CASEWORKER, 1L);
    tracker.applicationReady(store, MEMBER, 0L);

    List<AssignmentChange> changes = tracker.memberAdded(store, GROUP, MEMBER);

    assertThat(changes).containsExactly(new AssignmentChange(MEMBER, GROUP, null, CASEWORKER, 1L));
  }

  @Test
  void givenDecidedMember_whenGroupUnassigned_thenKeepsShownCaseworker() {
    tracker.applicationReady(store, APPLICATION, 0L);
    tracker.groupCreated(store, GROUP, List.of(APPLICATION));
    tracker.groupAssignmentChanged(store, GROUP, CASEWORKER, 1L);
    tracker.applicationDecided(store, APPLICATION);

    List<AssignmentChange> changes = tracker.groupAssignmentChanged(store, GROUP, null, 2L);

    assertThat(changes).isEmpty();
    assertThat(store.findApplication(APPLICATION))
        .contains(new TrackedApplication(APPLICATION, GROUP, false, CASEWORKER, 1L));
  }

  @Test
  void givenGroup_whenAssigned_thenOnlyActiveMembersChange() {
    tracker.applicationReady(store, APPLICATION, 0L);
    tracker.groupCreated(store, GROUP, List.of(APPLICATION, MEMBER));

    List<AssignmentChange> changes = tracker.groupAssignmentChanged(store, GROUP, CASEWORKER, 1L);

    assertThat(changes)
        .containsExactly(new AssignmentChange(APPLICATION, GROUP, null, CASEWORKER, 1L));
    assertThat(store.findApplication(MEMBER))
        .contains(new TrackedApplication(MEMBER, GROUP, false, null, 0L));
  }

  @Test
  void givenAssignedGroup_whenUnassigned_thenActiveMembersCleared() {
    tracker.applicationReady(store, APPLICATION, 0L);
    tracker.groupCreated(store, GROUP, List.of(APPLICATION));
    tracker.groupAssignmentChanged(store, GROUP, CASEWORKER, 1L);

    List<AssignmentChange> changes = tracker.groupAssignmentChanged(store, GROUP, null, 2L);

    assertThat(changes)
        .containsExactly(new AssignmentChange(APPLICATION, GROUP, CASEWORKER, null, 2L));
    assertThat(store.findApplication(APPLICATION))
        .contains(new TrackedApplication(APPLICATION, GROUP, true, null, 2L));
  }

  @Test
  void givenActiveGroupMember_whenInherited_thenTakesGroupAssignment() {
    tracker.groupCreated(store, GROUP, List.of(APPLICATION));
    tracker.applicationReady(store, APPLICATION, 0L);

    assertThat(tracker.memberInherited(store, GROUP, APPLICATION, CASEWORKER, 1L))
        .contains(new AssignmentChange(APPLICATION, GROUP, null, CASEWORKER, 1L));
  }

  @Test
  void givenActiveMember_whenReclaimed_thenBecomesDirectOwner() {
    tracker.groupCreated(store, GROUP, List.of(APPLICATION));
    tracker.applicationReady(store, APPLICATION, 0L);
    tracker.directAssignmentChanged(store, APPLICATION, CASEWORKER, 1L);

    assertThat(tracker.assignmentReclaimed(store, APPLICATION, CASEWORKER, 5L))
        .contains(new AssignmentChange(APPLICATION, null, CASEWORKER, CASEWORKER, 5L));
  }

  @Test
  void givenDecidedMember_whenReclaimed_thenPreservesDecisionAssignment() {
    tracker.groupCreated(store, GROUP, List.of(APPLICATION));
    tracker.applicationReady(store, APPLICATION, 0L);
    tracker.directAssignmentChanged(store, APPLICATION, CASEWORKER, 1L);
    tracker.applicationDecided(store, APPLICATION);

    assertThat(tracker.assignmentReclaimed(store, APPLICATION, null, 5L)).isEmpty();
    assertThat(store.findApplication(APPLICATION))
        .contains(new TrackedApplication(APPLICATION, null, false, CASEWORKER, 1L));
  }

  @Test
  void givenGroupAssignmentBeforeReadiness_whenEventsAreReplayed_thenMemberIsInherited() {
    tracker.groupCreated(store, GROUP, List.of(APPLICATION, MEMBER));
    tracker.groupAssignmentChanged(store, GROUP, CASEWORKER, 1L);

    tracker.applicationReady(store, MEMBER, 0L);
    tracker.memberInherited(store, GROUP, MEMBER, CASEWORKER, 1L);

    assertThat(store.findApplication(MEMBER))
        .contains(new TrackedApplication(MEMBER, GROUP, true, CASEWORKER, 1L));
  }

  @Test
  void givenGroupAssignmentAndReadiness_whenEitherOrder_thenMemberShowsGroupCaseworker() {
    InMemoryGroupAssignmentTrackingStore assignmentFirst =
        new InMemoryGroupAssignmentTrackingStore();
    InMemoryGroupAssignmentTrackingStore readyFirst = new InMemoryGroupAssignmentTrackingStore();
    for (InMemoryGroupAssignmentTrackingStore currentStore : List.of(assignmentFirst, readyFirst)) {
      tracker.groupCreated(currentStore, GROUP, List.of(APPLICATION, MEMBER));
    }

    List<AssignmentChange> assignmentFirstChanges = new ArrayList<>();
    assignmentFirstChanges.addAll(
        tracker.groupAssignmentChanged(assignmentFirst, GROUP, CASEWORKER, 1L));
    assignmentFirstChanges.add(tracker.applicationReady(assignmentFirst, MEMBER, 0L));
    tracker
        .memberInherited(assignmentFirst, GROUP, MEMBER, CASEWORKER, 1L)
        .ifPresent(assignmentFirstChanges::add);

    List<AssignmentChange> readyFirstChanges = new ArrayList<>();
    readyFirstChanges.add(tracker.applicationReady(readyFirst, MEMBER, 0L));
    readyFirstChanges.addAll(tracker.groupAssignmentChanged(readyFirst, GROUP, CASEWORKER, 1L));

    TrackedApplication expected = new TrackedApplication(MEMBER, GROUP, true, CASEWORKER, 1L);
    assertThat(assignmentFirst.findApplication(MEMBER)).contains(expected);
    assertThat(readyFirst.findApplication(MEMBER)).contains(expected);
    assertThat(changedCaseworkerChangesFor(assignmentFirstChanges, MEMBER)).hasSize(1);
    assertThat(changedCaseworkerChangesFor(readyFirstChanges, MEMBER)).hasSize(1);
  }

  private List<AssignmentChange> changedCaseworkerChangesFor(
      List<AssignmentChange> changes, UUID applicationId) {
    return changes.stream()
        .filter(change -> change.applicationId().equals(applicationId))
        .filter(AssignmentChange::caseworkerChanged)
        .toList();
  }

  @Test
  void givenGroupAssignmentAndJoin_whenEitherOrder_thenJoiningMemberShowsGroupCaseworker() {
    InMemoryGroupAssignmentTrackingStore joinFirst = new InMemoryGroupAssignmentTrackingStore();
    InMemoryGroupAssignmentTrackingStore readinessFirst =
        new InMemoryGroupAssignmentTrackingStore();
    for (InMemoryGroupAssignmentTrackingStore currentStore : List.of(joinFirst, readinessFirst)) {
      tracker.groupCreated(currentStore, GROUP, List.of(APPLICATION));
      tracker.groupAssignmentChanged(currentStore, GROUP, CASEWORKER, 1L);
    }

    tracker.memberAdded(joinFirst, GROUP, MEMBER);
    tracker.applicationReady(joinFirst, MEMBER, 0L);
    tracker.memberInherited(joinFirst, GROUP, MEMBER, CASEWORKER, 1L);

    tracker.applicationReady(readinessFirst, MEMBER, 0L);
    tracker.memberAdded(readinessFirst, GROUP, MEMBER);

    TrackedApplication expected = new TrackedApplication(MEMBER, GROUP, true, CASEWORKER, 1L);
    assertThat(joinFirst.findApplication(MEMBER)).contains(expected);
    assertThat(readinessFirst.findApplication(MEMBER)).contains(expected);
  }

  @Test
  void givenDecisionAndRemoval_whenEitherOrder_thenFormerMemberKeepsDecisionAssignee() {
    InMemoryGroupAssignmentTrackingStore decisionFirst = new InMemoryGroupAssignmentTrackingStore();
    InMemoryGroupAssignmentTrackingStore removalFirst = new InMemoryGroupAssignmentTrackingStore();
    for (InMemoryGroupAssignmentTrackingStore currentStore : List.of(decisionFirst, removalFirst)) {
      tracker.applicationReady(currentStore, MEMBER, 0L);
      tracker.groupCreated(currentStore, GROUP, List.of(MEMBER));
      tracker.groupAssignmentChanged(currentStore, GROUP, CASEWORKER, 1L);
    }

    tracker.applicationDecided(decisionFirst, MEMBER);
    tracker.membersLeft(decisionFirst, GROUP, List.of(MEMBER), false);
    tracker.assignmentReclaimed(decisionFirst, MEMBER, null, 2L);

    tracker.membersLeft(removalFirst, GROUP, List.of(MEMBER), false);
    tracker.assignmentReclaimed(removalFirst, MEMBER, CASEWORKER, 2L);
    tracker.applicationDecided(removalFirst, MEMBER);

    assertThat(decisionFirst.findApplication(MEMBER))
        .hasValueSatisfying(
            application -> {
              assertThat(application.groupId()).isNull();
              assertThat(application.active()).isFalse();
              assertThat(application.caseworkerId()).isEqualTo(CASEWORKER);
            });
    assertThat(removalFirst.findApplication(MEMBER))
        .hasValueSatisfying(
            application -> {
              assertThat(application.groupId()).isNull();
              assertThat(application.active()).isFalse();
              assertThat(application.caseworkerId()).isEqualTo(CASEWORKER);
            });
  }

  @Test
  void givenRepeatedReadinessAndGroupCreation_whenReplayed_thenNoDuplicateChanges() {
    AssignmentChange firstReady = tracker.applicationReady(store, APPLICATION, 2L);

    assertThat(tracker.applicationReady(store, APPLICATION, 3L)).isEqualTo(firstReady);
    assertThat(tracker.groupCreated(store, GROUP, List.of(APPLICATION)))
        .containsExactly(new AssignmentChange(APPLICATION, GROUP, null, null, 0L));
    assertThat(tracker.groupCreated(store, GROUP, List.of(APPLICATION))).isEmpty();
    assertThat(store.findApplication(APPLICATION))
        .contains(new TrackedApplication(APPLICATION, GROUP, true, null, 0L));
  }

  @Test
  void givenInactiveMember_whenAddedToUnknownGroup_thenTracksWithoutReportedChange() {
    assertThat(tracker.memberAdded(store, GROUP, MEMBER)).isEmpty();

    assertThat(store.findGroup(GROUP)).contains(new TrackedGroup(GROUP, null, 0L));
    assertThat(store.findApplication(MEMBER))
        .contains(new TrackedApplication(MEMBER, GROUP, false, null, 0L));
    assertThat(tracker.memberAdded(store, GROUP, MEMBER)).isEmpty();
  }

  @Test
  void givenGroupAssignment_whenMissingOrStaleGroupEvent_thenSeedsOrIgnoresEvent() {
    assertThat(tracker.groupAssignmentChanged(store, GROUP, CASEWORKER, 1L)).isEmpty();
    assertThat(tracker.groupAssignmentChanged(store, GROUP, null, 1L)).isEmpty();

    assertThat(store.findGroup(GROUP)).contains(new TrackedGroup(GROUP, CASEWORKER, 1L));
  }

  @Test
  void givenOtherGroupMember_whenLeaving_thenPreservedAndDissolvedGroupDeleted() {
    UUID otherGroup = UUID.fromString("00000000-0000-0000-0000-000000000011");
    tracker.groupCreated(store, otherGroup, List.of(APPLICATION));

    tracker.membersLeft(store, GROUP, List.of(APPLICATION), false);

    assertThat(store.findApplication(APPLICATION))
        .contains(new TrackedApplication(APPLICATION, otherGroup, false, null, 0L));
    tracker.membersLeft(store, otherGroup, List.of(APPLICATION), true);
    assertThat(store.findGroup(otherGroup)).isEmpty();
    assertThat(store.findApplication(APPLICATION))
        .contains(new TrackedApplication(APPLICATION, null, false, null, 0L));
  }

  @Test
  void givenMemberLeft_whenGroupAssigned_thenFormerMemberUnchanged() {
    tracker.applicationReady(store, APPLICATION, 0L);
    tracker.groupCreated(store, GROUP, List.of(APPLICATION));
    tracker.directAssignmentChanged(store, APPLICATION, OTHER_CASEWORKER, 1L);

    tracker.membersLeft(store, GROUP, List.of(APPLICATION), false);
    List<AssignmentChange> changes = tracker.groupAssignmentChanged(store, GROUP, CASEWORKER, 2L);

    assertThat(changes).isEmpty();
    assertThat(store.findApplication(APPLICATION))
        .contains(new TrackedApplication(APPLICATION, null, true, OTHER_CASEWORKER, 1L));
  }

  @Test
  void givenIneligibleMembers_whenAssignmentInherited_thenIgnored() {
    assertThat(tracker.memberInherited(store, GROUP, APPLICATION, CASEWORKER, 1L))
        .isEqualTo(Optional.empty());

    tracker.groupCreated(store, GROUP, List.of(APPLICATION));
    assertThat(tracker.memberInherited(store, GROUP, APPLICATION, CASEWORKER, 1L))
        .isEqualTo(Optional.empty());
    tracker.groupCreated(
        store, UUID.fromString("00000000-0000-0000-0000-000000000011"), List.of(MEMBER));
    tracker.applicationReady(store, MEMBER, 0L);
    assertThat(tracker.memberInherited(store, GROUP, MEMBER, CASEWORKER, 1L))
        .isEqualTo(Optional.empty());
  }

  @Test
  void givenUntrackedOrNotReadyMember_whenReclaimed_thenNoAssignmentChangeReported() {
    assertThat(tracker.assignmentReclaimed(store, APPLICATION, null, 5L)).isEmpty();
    tracker.groupCreated(store, GROUP, List.of(APPLICATION));

    assertThat(tracker.assignmentReclaimed(store, APPLICATION, null, 5L)).isEmpty();
    assertThat(store.findApplication(APPLICATION))
        .contains(new TrackedApplication(APPLICATION, null, false, null, 0L));
    assertThat(tracker.applicationReady(store, APPLICATION, 5L))
        .isEqualTo(new AssignmentChange(APPLICATION, null, null, null, 5L));
  }
}
