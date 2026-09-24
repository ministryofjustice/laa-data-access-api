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
        .command(new AddApplicationToLinkedGroupCommand(groupId, newMemberId, occurredAt))
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
        .command(new AddApplicationToLinkedGroupCommand(groupId, existingMemberId, occurredAt))
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
                UUID.randomUUID(), UUID.randomUUID(), Instant.parse("2026-07-15T08:00:00Z")))
        .then()
        .exception(IllegalStateException.class)
        .noEvents();
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
