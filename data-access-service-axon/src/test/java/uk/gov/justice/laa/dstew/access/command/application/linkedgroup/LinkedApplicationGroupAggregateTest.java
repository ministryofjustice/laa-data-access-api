package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.test.fixture.AxonTestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationLinkConflictException;
import uk.gov.justice.laa.dstew.access.exception.LinkedApplicationGroupVersionConflictException;

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
  void givenNoGroup_whenEstablish_thenEmitsGroupCreatedEvent() {
    UUID groupId = UUID.randomUUID();
    UUID leadApplicationId = UUID.randomUUID();
    UUID sourceApplicationId = UUID.randomUUID();
    List<UUID> members = List.of(leadApplicationId, sourceApplicationId);
    Instant occurredAt = Instant.parse("2026-07-15T08:00:00Z");

    fixture
        .given()
        .noPriorActivity()
        .when()
        .command(
            new EstablishLinkedApplicationGroupCommand(
                groupId, leadApplicationId, members, occurredAt))
        .then()
        .events(
            new LinkedApplicationGroupCreatedEvent(
                groupId, leadApplicationId, members, occurredAt));
  }

  @Test
  void givenGroupAlreadyEstablished_whenEquivalentEstablishRetried_thenNoEvents() {
    UUID groupId = UUID.randomUUID();
    UUID leadApplicationId = UUID.randomUUID();
    UUID sourceApplicationId = UUID.randomUUID();
    List<UUID> members = List.of(leadApplicationId, sourceApplicationId);
    Instant occurredAt = Instant.parse("2026-07-15T08:00:00Z");
    LinkedApplicationGroupCreatedEvent existing =
        new LinkedApplicationGroupCreatedEvent(groupId, leadApplicationId, members, occurredAt);

    fixture
        .given()
        .events(existing)
        .when()
        .command(
            new EstablishLinkedApplicationGroupCommand(
                groupId,
                leadApplicationId,
                List.of(sourceApplicationId, leadApplicationId),
                occurredAt))
        .then()
        .noEvents();
  }

  @Test
  void givenGroupHasLaterMemberAddition_whenOriginalEstablishRetried_thenNoEvents() {
    UUID groupId = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();
    UUID sourceApplicationId = UUID.randomUUID();
    UUID laterMemberId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-15T08:00:00Z");
    LinkedApplicationGroupCreatedEvent existing =
        new LinkedApplicationGroupCreatedEvent(
            groupId, leadId, List.of(leadId, sourceApplicationId), occurredAt);

    fixture
        .given()
        .events(existing, new MemberAddedToGroupEvent(groupId, leadId, laterMemberId, occurredAt))
        .when()
        .command(
            new EstablishLinkedApplicationGroupCommand(
                groupId, leadId, List.of(leadId, sourceApplicationId), occurredAt))
        .then()
        .noEvents();
  }

  @Test
  void givenGroupAlreadyEstablished_whenEstablishUsesDifferentLead_thenRejectsConflict() {
    UUID groupId = UUID.randomUUID();
    UUID currentLeadId = UUID.randomUUID();
    UUID differentLeadId = UUID.randomUUID();
    UUID sourceApplicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-15T08:00:00Z");
    LinkedApplicationGroupCreatedEvent existing =
        new LinkedApplicationGroupCreatedEvent(
            groupId, currentLeadId, List.of(currentLeadId, sourceApplicationId), occurredAt);

    fixture
        .given()
        .events(existing)
        .when()
        .command(
            new EstablishLinkedApplicationGroupCommand(
                groupId,
                differentLeadId,
                List.of(differentLeadId, sourceApplicationId),
                occurredAt))
        .then()
        .exception(ApplicationLinkConflictException.class)
        .noEvents();
  }

  @Test
  void
      givenGroupAlreadyEstablished_whenEstablishRequestsMemberMissingFromCurrentState_thenRejectsConflict() {
    UUID groupId = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();
    UUID sourceApplicationId = UUID.randomUUID();
    UUID newMemberId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-15T08:00:00Z");
    LinkedApplicationGroupCreatedEvent existing =
        new LinkedApplicationGroupCreatedEvent(
            groupId, leadId, List.of(leadId, sourceApplicationId), occurredAt);

    fixture
        .given()
        .events(existing)
        .when()
        .command(
            new EstablishLinkedApplicationGroupCommand(
                groupId, leadId, List.of(leadId, sourceApplicationId, newMemberId), occurredAt))
        .then()
        .exception(ApplicationLinkConflictException.class)
        .noEvents();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidEstablishMemberLists")
  void givenInvalidEstablishMemberList_whenEstablish_thenRejectsWithIllegalArgument(
      String scenario, UUID leadApplicationId, List<UUID> memberApplicationIds) {
    assertThatThrownBy(
            () ->
                new EstablishLinkedApplicationGroupCommand(
                    UUID.randomUUID(),
                    leadApplicationId,
                    memberApplicationIds,
                    Instant.parse("2026-07-15T08:00:00Z")))
        .as(scenario)
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void givenEstablishedGroup_whenAddApplication_thenEmitsMemberAddedEventWithCurrentLead() {
    UUID groupId = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();
    UUID existingMemberId = UUID.randomUUID();
    UUID newMemberId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-15T08:00:00Z");

    fixture
        .given()
        .events(
            new LinkedApplicationGroupCreatedEvent(
                groupId, leadId, List.of(leadId, existingMemberId), occurredAt))
        .when()
        .command(new AddApplicationToLinkedGroupCommand(groupId, newMemberId, 0, occurredAt))
        .then()
        .events(new MemberAddedToGroupEvent(groupId, leadId, newMemberId, occurredAt));
  }

  @Test
  void givenEstablishedGroup_whenAddApplicationAlreadyPresent_thenNoEvents() {
    UUID groupId = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();
    UUID existingMemberId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-15T08:00:00Z");

    fixture
        .given()
        .events(
            new LinkedApplicationGroupCreatedEvent(
                groupId, leadId, List.of(leadId, existingMemberId), occurredAt))
        .when()
        .command(new AddApplicationToLinkedGroupCommand(groupId, existingMemberId, 1, occurredAt))
        .then()
        .noEvents();
  }

  @Test
  void givenNoGroup_whenAddApplication_thenRejectsWithIllegalState() {
    fixture
        .given()
        .noPriorActivity()
        .when()
        .command(
            new AddApplicationToLinkedGroupCommand(
                UUID.randomUUID(), UUID.randomUUID(), 0, Instant.parse("2026-07-15T08:00:00Z")))
        .then()
        .exception(IllegalStateException.class)
        .noEvents();
  }

  @Test
  void givenStaleVersion_whenAddApplication_thenVersionConflict() {
    UUID groupId = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-15T08:00:00Z");

    fixture
        .given()
        .events(
            new LinkedApplicationGroupCreatedEvent(
                groupId, leadId, List.of(leadId, UUID.randomUUID()), occurredAt))
        .when()
        .command(new AddApplicationToLinkedGroupCommand(groupId, UUID.randomUUID(), 1, occurredAt))
        .then()
        .exception(LinkedApplicationGroupVersionConflictException.class)
        .noEvents();
  }

  @Test
  void givenAssociate_whenChangeLead_thenEmitsLeadChangedEvent() {
    UUID groupId = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();
    UUID associateId = UUID.randomUUID();
    List<UUID> members = List.of(leadId, associateId);
    Instant occurredAt = Instant.parse("2026-07-15T08:00:00Z");

    fixture
        .given()
        .events(new LinkedApplicationGroupCreatedEvent(groupId, leadId, members, occurredAt))
        .when()
        .command(new ChangeLinkedGroupLeadCommand(groupId, associateId, 0, occurredAt))
        .then()
        .events(
            new LinkedApplicationGroupLeadChangedEvent(
                groupId, leadId, associateId, 1, occurredAt));
  }

  @Test
  void givenCurrentLead_whenChangeLead_thenNoEvents() {
    UUID groupId = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-15T08:00:00Z");

    fixture
        .given()
        .events(
            new LinkedApplicationGroupCreatedEvent(
                groupId, leadId, List.of(leadId, UUID.randomUUID()), occurredAt))
        .when()
        .command(new ChangeLinkedGroupLeadCommand(groupId, leadId, 42, occurredAt))
        .then()
        .noEvents();
  }

  @Test
  void givenThreeMembers_whenRemoveAssociate_thenEmitsMemberRemoved() {
    UUID groupId = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();
    UUID removedId = UUID.randomUUID();
    UUID remainingId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-15T08:00:00Z");

    fixture
        .given()
        .events(
            new LinkedApplicationGroupCreatedEvent(
                groupId, leadId, List.of(leadId, removedId, remainingId), occurredAt))
        .when()
        .command(new RemoveApplicationFromLinkedGroupCommand(groupId, removedId, 0, occurredAt))
        .then()
        .events(new MemberRemovedFromGroupEvent(groupId, leadId, removedId, 1, occurredAt));
  }

  @Test
  void givenTwoMembers_whenRemoveAssociate_thenEmitsGroupDissolved() {
    UUID groupId = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();
    UUID removedId = UUID.randomUUID();
    List<UUID> members = List.of(leadId, removedId);
    Instant occurredAt = Instant.parse("2026-07-15T08:00:00Z");

    fixture
        .given()
        .events(new LinkedApplicationGroupCreatedEvent(groupId, leadId, members, occurredAt))
        .when()
        .command(new RemoveApplicationFromLinkedGroupCommand(groupId, removedId, 0, occurredAt))
        .then()
        .events(
            new LinkedApplicationGroupDissolvedEvent(
                groupId, leadId, removedId, members, 1, occurredAt));
  }

  @Test
  void givenLead_whenRemove_thenRejects() {
    UUID groupId = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-15T08:00:00Z");

    fixture
        .given()
        .events(
            new LinkedApplicationGroupCreatedEvent(
                groupId, leadId, List.of(leadId, UUID.randomUUID()), occurredAt))
        .when()
        .command(new RemoveApplicationFromLinkedGroupCommand(groupId, leadId, 0, occurredAt))
        .then()
        .exception(ApplicationLinkConflictException.class)
        .noEvents();
  }

  @Test
  void givenLeadChanged_whenRemovePreviousLead_thenEmitsMemberRemoved() {
    UUID groupId = UUID.randomUUID();
    UUID previousLeadId = UUID.randomUUID();
    UUID newLeadId = UUID.randomUUID();
    List<UUID> members = List.of(previousLeadId, newLeadId);
    Instant occurredAt = Instant.parse("2026-07-15T08:00:00Z");

    fixture
        .given()
        .events(
            new LinkedApplicationGroupCreatedEvent(groupId, previousLeadId, members, occurredAt),
            new LinkedApplicationGroupLeadChangedEvent(
                groupId, previousLeadId, newLeadId, 1, occurredAt))
        .when()
        .command(
            new RemoveApplicationFromLinkedGroupCommand(groupId, previousLeadId, 1, occurredAt))
        .then()
        .events(
            new LinkedApplicationGroupDissolvedEvent(
                groupId, newLeadId, previousLeadId, members, 2, occurredAt));
  }

  @Test
  void givenDissolvedGroup_whenAddApplication_thenRejects() {
    UUID groupId = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();
    UUID removedId = UUID.randomUUID();
    List<UUID> members = List.of(leadId, removedId);
    Instant occurredAt = Instant.parse("2026-07-15T08:00:00Z");

    fixture
        .given()
        .events(
            new LinkedApplicationGroupCreatedEvent(groupId, leadId, members, occurredAt),
            new LinkedApplicationGroupDissolvedEvent(
                groupId, leadId, removedId, members, 1, occurredAt))
        .when()
        .command(new AddApplicationToLinkedGroupCommand(groupId, UUID.randomUUID(), 0, occurredAt))
        .then()
        .exception(ApplicationLinkConflictException.class)
        .noEvents();
  }

  @Test
  void givenMemberRemoved_whenRemoveSameMemberAgain_thenRejects() {
    UUID groupId = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();
    UUID removedId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-15T08:00:00Z");

    fixture
        .given()
        .events(
            new LinkedApplicationGroupCreatedEvent(
                groupId, leadId, List.of(leadId, removedId, UUID.randomUUID()), occurredAt),
            new MemberRemovedFromGroupEvent(groupId, leadId, removedId, 1, occurredAt))
        .when()
        .command(new RemoveApplicationFromLinkedGroupCommand(groupId, removedId, 1, occurredAt))
        .then()
        .exception(ApplicationLinkConflictException.class)
        .noEvents();
  }

  @Test
  void givenStaleVersion_whenChangeLead_thenVersionConflict() {
    UUID groupId = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();
    UUID associateId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-15T08:00:00Z");

    fixture
        .given()
        .events(
            new LinkedApplicationGroupCreatedEvent(
                groupId, leadId, List.of(leadId, associateId), occurredAt))
        .when()
        .command(new ChangeLinkedGroupLeadCommand(groupId, associateId, 1, occurredAt))
        .then()
        .exception(LinkedApplicationGroupVersionConflictException.class)
        .noEvents();
  }

  @Test
  void givenStaleVersion_whenRemove_thenVersionConflict() {
    UUID groupId = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();
    UUID associateId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-15T08:00:00Z");

    fixture
        .given()
        .events(
            new LinkedApplicationGroupCreatedEvent(
                groupId, leadId, List.of(leadId, associateId), occurredAt))
        .when()
        .command(new RemoveApplicationFromLinkedGroupCommand(groupId, associateId, 1, occurredAt))
        .then()
        .exception(LinkedApplicationGroupVersionConflictException.class)
        .noEvents();
  }

  @Test
  void givenStaleVersionAndCurrentLead_whenChangeLead_thenNoEvents() {
    UUID groupId = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-15T08:00:00Z");

    fixture
        .given()
        .events(
            new LinkedApplicationGroupCreatedEvent(
                groupId, leadId, List.of(leadId, UUID.randomUUID()), occurredAt))
        .when()
        .command(new ChangeLinkedGroupLeadCommand(groupId, leadId, 42, occurredAt))
        .then()
        .noEvents();
  }

  @Test
  void givenMemberAdded_whenChangeLeadAtVersionOne_thenEmitsVersionTwo() {
    UUID groupId = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();
    UUID associateId = UUID.randomUUID();
    UUID newMemberId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-15T08:00:00Z");

    fixture
        .given()
        .events(
            new LinkedApplicationGroupCreatedEvent(
                groupId, leadId, List.of(leadId, associateId), occurredAt),
            new MemberAddedToGroupEvent(groupId, leadId, newMemberId, occurredAt))
        .when()
        .command(new ChangeLinkedGroupLeadCommand(groupId, associateId, 1, occurredAt))
        .then()
        .events(
            new LinkedApplicationGroupLeadChangedEvent(
                groupId, leadId, associateId, 2, occurredAt));
  }

  private static List<Arguments> invalidEstablishMemberLists() {
    UUID leadApplicationId = UUID.randomUUID();
    UUID otherMemberId = UUID.randomUUID();
    return List.of(
        Arguments.of("empty member list", leadApplicationId, List.of()),
        Arguments.of("missing lead application", leadApplicationId, List.of(otherMemberId)),
        Arguments.of(
            "duplicate member ids",
            leadApplicationId,
            List.of(leadApplicationId, otherMemberId, otherMemberId)));
  }

  @AfterEach
  void tearDown() {
    fixture.stop();
  }
}
