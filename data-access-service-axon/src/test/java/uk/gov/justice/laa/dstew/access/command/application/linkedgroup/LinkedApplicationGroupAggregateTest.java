package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.test.fixture.AxonTestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LinkedApplicationGroupAggregateTest {

  private AxonTestFixture fixture;

  @BeforeEach
  void setUp() {
    fixture =
        AxonTestFixture.with(
            EventSourcingConfigurer.create()
                .registerEntity(
                    EventSourcedEntityModule.autodetected(
                        UUID.class, LinkedApplicationGroupAggregate.class)));
  }

  @Test
  void givenNoGroup_whenInitialise_thenEmitsGroupCreatedEvent() {
    UUID groupId = UUID.randomUUID();
    UUID associatedId = UUID.randomUUID();
    List<UUID> members = List.of(groupId, associatedId);
    Instant occurredAt = Instant.parse("2026-07-15T08:00:00Z");

    fixture
        .given()
        .noPriorActivity()
        .when()
        .command(new InitialiseLinkedApplicationGroupCommand(groupId, groupId, members, occurredAt))
        .then()
        .events(new LinkedApplicationGroupCreatedEvent(groupId, groupId, members, occurredAt));
  }

  @Test
  void givenGroupAlreadyInitialised_whenRetryInitialise_thenNoEvents() {
    UUID groupId = UUID.randomUUID();
    UUID associatedId = UUID.randomUUID();
    List<UUID> members = List.of(groupId, associatedId);
    Instant occurredAt = Instant.parse("2026-07-15T08:00:00Z");
    LinkedApplicationGroupCreatedEvent existing =
        new LinkedApplicationGroupCreatedEvent(groupId, groupId, members, occurredAt);

    fixture
        .given()
        .events(existing)
        .when()
        .command(new InitialiseLinkedApplicationGroupCommand(groupId, groupId, members, occurredAt))
        .then()
        .noEvents();
  }

  @Test
  void givenGroupAlreadyExists_whenNewMemberJoins_thenEmitsMemberAddedEvent() {
    UUID groupId = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();
    UUID firstMemberId = UUID.randomUUID();
    UUID newMemberId = UUID.randomUUID();
    List<UUID> originalMembers = List.of(leadId, firstMemberId);
    Instant occurredAt = Instant.parse("2026-07-15T08:00:00Z");
    LinkedApplicationGroupCreatedEvent existing =
        new LinkedApplicationGroupCreatedEvent(groupId, leadId, originalMembers, occurredAt);

    fixture
        .given()
        .events(existing)
        .when()
        .command(
            new InitialiseLinkedApplicationGroupCommand(
                groupId, leadId, List.of(leadId, newMemberId), occurredAt))
        .then()
        .events(new MemberAddedToGroupEvent(groupId, newMemberId, occurredAt));
  }

  @Test
  void givenLeadNotInMemberList_whenInitialise_thenRejectsWithIllegalArgument() {
    UUID groupId = UUID.randomUUID();
    UUID someOtherId = UUID.randomUUID();
    UUID differentLeadId = UUID.randomUUID();
    List<UUID> membersWithoutLead = List.of(someOtherId);

    fixture
        .given()
        .noPriorActivity()
        .when()
        .command(
            new InitialiseLinkedApplicationGroupCommand(
                groupId,
                differentLeadId,
                membersWithoutLead,
                Instant.parse("2026-07-15T08:00:00Z")))
        .then()
        .exception(IllegalArgumentException.class)
        .noEvents();
  }

  @AfterEach
  void tearDown() {
    fixture.stop();
  }
}
