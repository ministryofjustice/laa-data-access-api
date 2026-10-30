package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.axonframework.test.fixture.AxonTestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.AssignLinkedGroupCommand;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.DelegatedAssignment;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.InheritLinkedGroupAssignmentCommand;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.LinkedGroupAssignedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.LinkedGroupMemberAssignmentInheritedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.LinkedGroupUnassignedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.UnassignLinkedGroupCommand;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.ValidateLinkedGroupAssigneeCommand;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssignmentConflictException;
import uk.gov.justice.laa.dstew.access.testutils.LinkedGroupTestFixture;

@ExtendWith(MockitoExtension.class)
class LinkedApplicationGroupAssignmentAggregateTest {

  private static final UUID GROUP_ID = UUID.randomUUID();
  private static final UUID LEAD_ID = UUID.randomUUID();
  private static final UUID MEMBER_ID = UUID.randomUUID();
  private static final UUID OTHER_MEMBER_ID = UUID.randomUUID();
  private static final UUID C1 = UUID.randomUUID();
  private static final UUID C2 = UUID.randomUUID();
  private static final Instant AT = Instant.parse("2026-10-22T09:00:00Z");

  private AxonTestFixture fixture;

  @BeforeEach
  void setUp() {
    fixture = LinkedGroupTestFixture.linkedGroupFixture();
  }

  @AfterEach
  void tearDown() {
    fixture.stop();
  }

  @Test
  void givenAssignedLead_whenGroupEstablished_thenCreatedThenAssigned() {
    var members = List.of(LEAD_ID, MEMBER_ID);

    fixture
        .given()
        .noPriorActivity()
        .when()
        .command(
            new EstablishLinkedApplicationGroupCommand(
                GROUP_ID,
                LEAD_ID,
                members,
                Map.of(
                    LEAD_ID, new DelegatedAssignment(C1),
                    MEMBER_ID, new DelegatedAssignment(null)),
                AT))
        .then()
        .events(
            new LinkedApplicationGroupCreatedEvent(GROUP_ID, LEAD_ID, members, AT),
            new LinkedGroupAssignedEvent(GROUP_ID, C1, 1, AT));
  }

  @Test
  void givenUnassignedGroup_whenAssignCommand_thenGroupAssigned() {
    fixture
        .given()
        .events(createdEvent())
        .when()
        .command(new AssignLinkedGroupCommand(GROUP_ID, MEMBER_ID, C1, 0, AT))
        .then()
        .events(new LinkedGroupAssignedEvent(GROUP_ID, C1, 1, AT));
  }

  @Test
  void givenAssignedGroup_whenAssignCommand_thenConflict() {
    fixture
        .given()
        .events(createdEvent(), new LinkedGroupAssignedEvent(GROUP_ID, C1, 1, AT))
        .when()
        .command(new AssignLinkedGroupCommand(GROUP_ID, MEMBER_ID, C2, 1, AT))
        .then()
        .exception(WorkItemAssignmentConflictException.class)
        .noEvents();
  }

  @Test
  void givenAssignedGroup_whenUnassignCommand_thenGroupUnassigned() {
    fixture
        .given()
        .events(createdEvent(), new LinkedGroupAssignedEvent(GROUP_ID, C1, 1, AT))
        .when()
        .command(new UnassignLinkedGroupCommand(GROUP_ID, MEMBER_ID, 1, AT))
        .then()
        .events(new LinkedGroupUnassignedEvent(GROUP_ID, 2, AT));
  }

  @Test
  void givenAssignedGroup_whenInheritCommand_thenInheritedEvent() {
    fixture
        .given()
        .events(createdEvent(), new LinkedGroupAssignedEvent(GROUP_ID, C1, 1, AT))
        .when()
        .command(new InheritLinkedGroupAssignmentCommand(GROUP_ID, MEMBER_ID, AT))
        .then()
        .events(new LinkedGroupMemberAssignmentInheritedEvent(GROUP_ID, MEMBER_ID, C1, 1, AT));
  }

  @Test
  void givenAssignedGroup_whenValidateMatchingAssignee_thenNoEvents() {
    fixture
        .given()
        .events(createdEvent(), new LinkedGroupAssignedEvent(GROUP_ID, C1, 1, AT))
        .when()
        .command(new ValidateLinkedGroupAssigneeCommand(GROUP_ID, MEMBER_ID, C1))
        .then()
        .success()
        .noEvents();
  }

  @Test
  void givenAssignedGroup_whenMemberRemoved_thenReturnsRemovalResult() {
    var members = List.of(LEAD_ID, MEMBER_ID, OTHER_MEMBER_ID);

    fixture
        .given()
        .events(
            new LinkedApplicationGroupCreatedEvent(GROUP_ID, LEAD_ID, members, AT),
            new LinkedGroupAssignedEvent(GROUP_ID, C1, 1, AT))
        .when()
        .command(new RemoveApplicationFromLinkedGroupCommand(GROUP_ID, MEMBER_ID, 0, AT))
        .then()
        .events(new MemberRemovedFromGroupEvent(GROUP_ID, LEAD_ID, MEMBER_ID, 1, C1, 1, AT))
        .resultMessagePayload(new LinkedGroupRemovalResult(GROUP_ID, List.of(MEMBER_ID), C1, 1));
  }

  @Test
  void givenUnassignedHistory_whenAssignedAgain_thenUsesRebuiltVersion() {
    fixture
        .given()
        .events(
            createdEvent(),
            new LinkedGroupAssignedEvent(GROUP_ID, C1, 1, AT),
            new LinkedGroupUnassignedEvent(GROUP_ID, 2, AT))
        .when()
        .command(new AssignLinkedGroupCommand(GROUP_ID, MEMBER_ID, C2, 2, AT))
        .then()
        .events(new LinkedGroupAssignedEvent(GROUP_ID, C2, 3, AT));
  }

  @Test
  void givenStaleAssignmentVersion_whenAssignCommand_thenConflict() {
    fixture
        .given()
        .events(createdEvent())
        .when()
        .command(new AssignLinkedGroupCommand(GROUP_ID, MEMBER_ID, C1, 1, AT))
        .then()
        .exception(WorkItemAssignmentConflictException.class)
        .noEvents();
  }

  private static LinkedApplicationGroupCreatedEvent createdEvent() {
    return new LinkedApplicationGroupCreatedEvent(
        GROUP_ID, LEAD_ID, List.of(LEAD_ID, MEMBER_ID), AT);
  }
}
