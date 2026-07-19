package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LinkedApplicationGroupAggregateTest {

  private AggregateTestFixture<LinkedApplicationGroupAggregate> fixture;

  @BeforeEach
  void setUp() {
    fixture = new AggregateTestFixture<>(LinkedApplicationGroupAggregate.class);
  }

  @Test
  void givenNoGroup_whenInitialise_thenEmitsGroupCreatedEvent() {
    UUID groupId = UUID.randomUUID();
    UUID associatedId = UUID.randomUUID();
    List<UUID> members = List.of(groupId, associatedId);
    Instant occurredAt = Instant.parse("2026-07-15T08:00:00Z");

    fixture
        .givenNoPriorActivity()
        .when(new InitialiseLinkedApplicationGroupCommand(groupId, groupId, members, occurredAt))
        .expectEvents(
            new LinkedApplicationGroupCreatedEvent(groupId, groupId, members, occurredAt));
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
        .given(existing)
        .when(new InitialiseLinkedApplicationGroupCommand(groupId, groupId, members, occurredAt))
        .expectNoEvents();
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
        .given(existing)
        .when(
            new InitialiseLinkedApplicationGroupCommand(
                groupId, leadId, List.of(leadId, newMemberId), occurredAt))
        .expectEvents(new MemberAddedToGroupEvent(groupId, newMemberId, occurredAt));
  }

  @Test
  void givenLeadNotInMemberList_whenInitialise_thenRejectsWithIllegalArgument() {
    UUID groupId = UUID.randomUUID();
    UUID someOtherId = UUID.randomUUID();
    UUID differentLeadId = UUID.randomUUID();
    List<UUID> membersWithoutLead = List.of(someOtherId);

    fixture
        .givenNoPriorActivity()
        .when(
            new InitialiseLinkedApplicationGroupCommand(
                groupId,
                differentLeadId,
                membersWithoutLead,
                Instant.parse("2026-07-15T08:00:00Z")))
        .expectException(IllegalArgumentException.class)
        .expectNoEvents();
  }
}
