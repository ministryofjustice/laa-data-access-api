package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import static uk.gov.justice.laa.dstew.access.command.application.ApplicationStateEvents.activeUnassigned;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.axonframework.test.fixture.AxonTestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.testutils.LinkedGroupTestFixture;

class LinkedApplicationGroupAssignmentTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-10-27T12:00:00Z");
  private static final UUID GROUP_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID LEAD_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final UUID MEMBER_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
  private static final UUID JOINING_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");

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
  void givenUnassignedMembers_whenEstablish_thenGroupEventOnly() {
    var groupEvent =
        new LinkedApplicationGroupCreatedEvent(
            GROUP_ID, LEAD_ID, List.of(LEAD_ID, MEMBER_ID), OCCURRED_AT);

    fixture
        .given()
        .events(fixtureEvents(activeUnassigned(LEAD_ID), activeUnassigned(MEMBER_ID)))
        .when()
        .command(
            new EstablishLinkedApplicationGroupCommand(
                GROUP_ID, LEAD_ID, List.of(LEAD_ID, MEMBER_ID), OCCURRED_AT))
        .then()
        .events(groupEvent);
  }

  @Test
  void givenGroupAlreadyEstablished_whenEstablishRetried_thenNoEvents() {
    var groupEvent =
        new LinkedApplicationGroupCreatedEvent(
            GROUP_ID, LEAD_ID, List.of(LEAD_ID, MEMBER_ID), OCCURRED_AT);

    fixture
        .given()
        .events(groupEvent)
        .when()
        .command(
            new EstablishLinkedApplicationGroupCommand(
                GROUP_ID, LEAD_ID, List.of(MEMBER_ID, LEAD_ID), OCCURRED_AT))
        .then()
        .noEvents();
  }

  @Test
  void givenUnassignedGroupAndMember_whenAdd_thenGroupEventOnly() {
    var groupEvent =
        new LinkedApplicationGroupCreatedEvent(
            GROUP_ID, LEAD_ID, List.of(LEAD_ID, MEMBER_ID), OCCURRED_AT);

    fixture
        .given()
        .events(
            fixtureEvents(
                groupEvent,
                activeUnassigned(LEAD_ID),
                activeUnassigned(MEMBER_ID),
                activeUnassigned(JOINING_ID)))
        .when()
        .command(new AddApplicationToLinkedGroupCommand(GROUP_ID, JOINING_ID, 0L, OCCURRED_AT))
        .then()
        .events(new MemberAddedToGroupEvent(GROUP_ID, LEAD_ID, JOINING_ID, OCCURRED_AT));
  }

  @Test
  void givenAlreadyMember_whenAdd_thenNoEvents() {
    var groupEvent =
        new LinkedApplicationGroupCreatedEvent(
            GROUP_ID, LEAD_ID, List.of(LEAD_ID, MEMBER_ID), OCCURRED_AT);

    fixture
        .given()
        .events(groupEvent)
        .when()
        .command(new AddApplicationToLinkedGroupCommand(GROUP_ID, MEMBER_ID, 0L, OCCURRED_AT))
        .then()
        .noEvents();
  }

  private Object[] fixtureEvents(Object... eventsOrHistories) {
    var events = new ArrayList<>();
    for (var eventOrHistory : eventsOrHistories) {
      if (eventOrHistory instanceof List<?> history) {
        events.addAll(history);
      } else {
        events.add(eventOrHistory);
      }
    }
    return events.toArray();
  }
}
