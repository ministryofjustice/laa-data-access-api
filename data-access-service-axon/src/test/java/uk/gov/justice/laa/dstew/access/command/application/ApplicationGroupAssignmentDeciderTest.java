package uk.gov.justice.laa.dstew.access.command.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.command.application.decision.MakeApplicationDecisionCommand;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.ApplicationAssignmentDelegatedToGroupEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.ApplicationAssignmentReclaimedFromGroupEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.DelegateAssignmentToGroupCommand;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.DelegatedAssignment;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.ReclaimAssignmentFromGroupCommand;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationLinkConflictException;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssignmentConflictException;

@ExtendWith(MockitoExtension.class)
class ApplicationGroupAssignmentDeciderTest {

  private static final UUID APPLICATION_ID = UUID.randomUUID();
  private static final UUID GROUP_ID = UUID.randomUUID();
  private static final UUID OTHER_GROUP_ID = UUID.randomUUID();
  private static final UUID CASEWORKER_ID = UUID.randomUUID();
  private static final Instant OCCURRED_AT = Instant.parse("2026-10-01T10:00:00Z");

  @ParameterizedTest(name = "{0}")
  @MethodSource("handOverCases")
  void givenApplicationState_whenDelegated_thenHandsOverActiveCaseworker(
      String scenario,
      AutoGrantedState autoGranted,
      String overallDecision,
      UUID caseworkerId,
      UUID expected) {
    ApplicationState state = state(autoGranted, overallDecision, caseworkerId);

    assertThat(ApplicationDecider.delegatedAssignment(state))
        .isEqualTo(new DelegatedAssignment(expected));
  }

  @Test
  void givenStandaloneApplication_whenDelegated_thenCreatesDelegationEvent() {
    ApplicationState state = state(AutoGrantedState.MANUAL, null, null);

    assertThat(
            ApplicationDecider.decideDelegateToGroup(
                state, new DelegateAssignmentToGroupCommand(APPLICATION_ID, GROUP_ID, OCCURRED_AT)))
        .contains(
            new ApplicationAssignmentDelegatedToGroupEvent(APPLICATION_ID, GROUP_ID, OCCURRED_AT));
  }

  @Test
  void givenSameGroup_whenDelegatedAgain_thenAppendsNothing() {
    ApplicationState state = state(AutoGrantedState.MANUAL, null, null);
    state.assignmentGroupId = GROUP_ID;

    assertThat(
            ApplicationDecider.decideDelegateToGroup(
                state, new DelegateAssignmentToGroupCommand(APPLICATION_ID, GROUP_ID, OCCURRED_AT)))
        .isEmpty();
  }

  @Test
  void givenOtherGroup_whenDelegated_thenRejectsWithLinkConflict() {
    ApplicationState state = state(AutoGrantedState.MANUAL, null, null);
    state.assignmentGroupId = GROUP_ID;

    assertThatThrownBy(
            () ->
                ApplicationDecider.decideDelegateToGroup(
                    state,
                    new DelegateAssignmentToGroupCommand(
                        APPLICATION_ID, OTHER_GROUP_ID, OCCURRED_AT)))
        .isInstanceOf(ApplicationLinkConflictException.class)
        .hasMessage(
            "Application " + APPLICATION_ID + " already belongs to a different linked group");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("reclaimCases")
  void givenLeavingMember_whenReclaimed_thenOnlyActiveTakesGroupCaseworker(
      String scenario,
      AutoGrantedState autoGranted,
      String overallDecision,
      UUID caseworkerId,
      UUID expected) {
    ApplicationState state = state(autoGranted, overallDecision, caseworkerId);
    state.assignmentGroupId = GROUP_ID;

    assertThat(
            ApplicationDecider.decideReclaimFromGroup(
                state,
                new ReclaimAssignmentFromGroupCommand(
                    APPLICATION_ID, GROUP_ID, CASEWORKER_ID, 5L, OCCURRED_AT)))
        .contains(
            new ApplicationAssignmentReclaimedFromGroupEvent(
                APPLICATION_ID, GROUP_ID, expected, 6L, OCCURRED_AT));
  }

  @ParameterizedTest
  @CsvSource({"2,5,6", "7,5,8", "5,5,6"})
  void givenVersions_whenReclaimed_thenVersionPassesBoth(
      long applicationVersion, long groupVersion, long expected) {
    assertReclaimedVersion(applicationVersion, groupVersion, expected);
  }

  @Test
  void givenNoDelegation_whenReclaimed_thenAppendsNothing() {
    ApplicationState state = state(AutoGrantedState.MANUAL, null, null);

    assertThat(
            ApplicationDecider.decideReclaimFromGroup(
                state,
                new ReclaimAssignmentFromGroupCommand(
                    APPLICATION_ID, GROUP_ID, CASEWORKER_ID, 5L, OCCURRED_AT)))
        .isEmpty();
  }

  @Test
  void givenOtherGroup_whenReclaimed_thenRejectsWithLinkConflict() {
    ApplicationState state = state(AutoGrantedState.MANUAL, null, null);
    state.assignmentGroupId = GROUP_ID;

    assertThatThrownBy(
            () ->
                ApplicationDecider.decideReclaimFromGroup(
                    state,
                    new ReclaimAssignmentFromGroupCommand(
                        APPLICATION_ID, OTHER_GROUP_ID, CASEWORKER_ID, 5L, OCCURRED_AT)))
        .isInstanceOf(ApplicationLinkConflictException.class)
        .hasMessage(
            "Application "
                + APPLICATION_ID
                + " does not delegate its assignment to linked group "
                + OTHER_GROUP_ID);
  }

  @Test
  void givenGroupedApplication_whenValidatedForItsGroup_thenSkipsOwnAssignmentCheck() {
    ApplicationState state = state(AutoGrantedState.MANUAL, null, null);
    state.assignmentGroupId = GROUP_ID;

    ApplicationDecider.validateManualDecisionAssignment(state, decisionCommand(GROUP_ID));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("unvalidatedMarkers")
  void givenGroupedApplication_whenNotValidatedForGroup_thenRejects(String scenario, UUID marker) {
    ApplicationState state = state(AutoGrantedState.MANUAL, null, null);
    state.assignmentGroupId = GROUP_ID;

    assertThatThrownBy(
            () ->
                ApplicationDecider.validateManualDecisionAssignment(state, decisionCommand(marker)))
        .isInstanceOf(WorkItemAssignmentConflictException.class)
        .hasMessageEndingWith("its linked-group assignee was not validated");
  }

  @Test
  void givenDecidedGroupedApplication_whenDecided_thenRejectsAsInactive() {
    ApplicationState state = state(AutoGrantedState.MANUAL, "REFUSED", null);
    state.assignmentGroupId = GROUP_ID;

    assertThatThrownBy(
            () ->
                ApplicationDecider.validateManualDecisionAssignment(
                    state, decisionCommand(GROUP_ID)))
        .isInstanceOf(WorkItemAssignmentConflictException.class)
        .hasMessageEndingWith("it is not an active manual work item");
  }

  @Test
  void givenStandaloneApplication_whenMarkerSet_thenOwnCaseworkerStillChecked() {
    ApplicationState state = state(AutoGrantedState.MANUAL, null, UUID.randomUUID());

    assertThatThrownBy(
            () ->
                ApplicationDecider.validateManualDecisionAssignment(
                    state, decisionCommand(GROUP_ID)))
        .isInstanceOf(WorkItemAssignmentConflictException.class)
        .hasMessageEndingWith("the supplied caseworker is not its current assignee");
  }

  private static ApplicationState state(
      AutoGrantedState autoGranted, String decision, UUID caseworkerId) {
    ApplicationState state = new ApplicationState();
    state.applicationId = APPLICATION_ID;
    state.autoGranted = autoGranted;
    state.overallDecision = decision;
    state.caseworkerId = caseworkerId;
    return state;
  }

  private static MakeApplicationDecisionCommand decisionCommand(UUID validatedGroupId) {
    return new MakeApplicationDecisionCommand(
        APPLICATION_ID,
        CASEWORKER_ID,
        0L,
        "REFUSED",
        List.of(),
        null,
        "{}",
        null,
        OCCURRED_AT,
        validatedGroupId);
  }

  private static void assertReclaimedVersion(
      long applicationVersion, long groupVersion, long expectedVersion) {
    ApplicationState state = state(AutoGrantedState.MANUAL, null, null);
    state.assignmentGroupId = GROUP_ID;
    state.assignmentVersion = applicationVersion;

    var event =
        ApplicationDecider.decideReclaimFromGroup(
                state,
                new ReclaimAssignmentFromGroupCommand(
                    APPLICATION_ID, GROUP_ID, null, groupVersion, OCCURRED_AT))
            .orElseThrow();

    assertThat(event.assignmentVersion()).isEqualTo(expectedVersion);
  }

  private static Stream<Arguments> handOverCases() {
    return Stream.of(
        Arguments.of(
            "active assigned", AutoGrantedState.MANUAL, null, CASEWORKER_ID, CASEWORKER_ID),
        Arguments.of("active unassigned", AutoGrantedState.MANUAL, null, null, null),
        Arguments.of("not ready", AutoGrantedState.PENDING, null, CASEWORKER_ID, null),
        Arguments.of("decided", AutoGrantedState.MANUAL, "GRANTED", CASEWORKER_ID, null),
        Arguments.of("auto-granted", AutoGrantedState.AUTOGRANTED, "GRANTED", null, null));
  }

  private static Stream<Arguments> reclaimCases() {
    return Stream.of(
        Arguments.of("active", AutoGrantedState.MANUAL, null, null, CASEWORKER_ID),
        Arguments.of("not ready", AutoGrantedState.PENDING, null, null, null),
        Arguments.of("decided", AutoGrantedState.MANUAL, "REFUSED", CASEWORKER_ID, null),
        Arguments.of("auto-granted", AutoGrantedState.AUTOGRANTED, "GRANTED", null, null));
  }

  private static Stream<Arguments> unvalidatedMarkers() {
    return Stream.of(Arguments.of("missing", null), Arguments.of("other group", OTHER_GROUP_ID));
  }
}
