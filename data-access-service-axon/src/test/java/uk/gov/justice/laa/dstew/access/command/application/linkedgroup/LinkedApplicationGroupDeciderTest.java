package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Pure unit tests for {@link LinkedApplicationGroupDecider} — no Spring, no Axon, no database. */
class LinkedApplicationGroupDeciderTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-07-15T08:00:00Z");

  @Test
  void givenNoGroup_whenDecideInitialise_thenReturnsGroupCreatedEvent() {
    LinkedApplicationGroupState state = new LinkedApplicationGroupState();
    UUID groupId = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();
    UUID memberId = UUID.randomUUID();
    List<UUID> members = List.of(leadId, memberId);

    List<Object> events =
        LinkedApplicationGroupDecider.decideInitialise(
            state,
            new InitialiseLinkedApplicationGroupCommand(groupId, leadId, members, OCCURRED_AT));

    assertThat(events).hasSize(1);
    LinkedApplicationGroupCreatedEvent event =
        (LinkedApplicationGroupCreatedEvent) events.getFirst();
    assertThat(event.groupId()).isEqualTo(groupId);
    assertThat(event.leadApplicationId()).isEqualTo(leadId);
    assertThat(event.memberApplicationIds()).containsExactlyElementsOf(members);
    assertThat(event.occurredAt()).isEqualTo(OCCURRED_AT);
  }

  @Test
  void givenLeadNotInMemberList_whenDecideInitialise_thenThrowsIllegalArgument() {
    LinkedApplicationGroupState state = new LinkedApplicationGroupState();
    UUID groupId = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();
    List<UUID> membersWithoutLead = List.of(UUID.randomUUID());

    assertThatThrownBy(
            () ->
                LinkedApplicationGroupDecider.decideInitialise(
                    state,
                    new InitialiseLinkedApplicationGroupCommand(
                        groupId, leadId, membersWithoutLead, OCCURRED_AT)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(leadId.toString());
  }

  @Test
  void givenExistingGroup_whenDecideInitialiseWithSameMembers_thenReturnsEmptyList() {
    UUID groupId = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();
    UUID memberId = UUID.randomUUID();
    LinkedApplicationGroupState state =
        stateAfterCreate(groupId, leadId, List.of(leadId, memberId));

    List<Object> events =
        LinkedApplicationGroupDecider.decideInitialise(
            state,
            new InitialiseLinkedApplicationGroupCommand(
                groupId, leadId, List.of(leadId, memberId), OCCURRED_AT));

    assertThat(events).isEmpty();
  }

  @Test
  void givenExistingGroup_whenDecideInitialiseWithNewMember_thenReturnsMemberAddedEvent() {
    UUID groupId = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();
    UUID existingMemberId = UUID.randomUUID();
    UUID newMemberId = UUID.randomUUID();
    LinkedApplicationGroupState state =
        stateAfterCreate(groupId, leadId, List.of(leadId, existingMemberId));

    List<Object> events =
        LinkedApplicationGroupDecider.decideInitialise(
            state,
            new InitialiseLinkedApplicationGroupCommand(
                groupId, leadId, List.of(leadId, newMemberId), OCCURRED_AT));

    assertThat(events).hasSize(1);
    MemberAddedToGroupEvent event = (MemberAddedToGroupEvent) events.getFirst();
    assertThat(event.groupId()).isEqualTo(groupId);
    assertThat(event.memberId()).isEqualTo(newMemberId);
    assertThat(event.occurredAt()).isEqualTo(OCCURRED_AT);
  }

  @Test
  void givenExistingGroup_whenDecideInitialiseWithMultipleNewMembers_thenReturnsEventsForEach() {
    UUID groupId = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();
    UUID existingMemberId = UUID.randomUUID();
    UUID newMemberId1 = UUID.randomUUID();
    UUID newMemberId2 = UUID.randomUUID();
    LinkedApplicationGroupState state =
        stateAfterCreate(groupId, leadId, List.of(leadId, existingMemberId));

    List<Object> events =
        LinkedApplicationGroupDecider.decideInitialise(
            state,
            new InitialiseLinkedApplicationGroupCommand(
                groupId, leadId, List.of(leadId, newMemberId1, newMemberId2), OCCURRED_AT));

    assertThat(events).hasSize(2);
    assertThat(events)
        .extracting(e -> ((MemberAddedToGroupEvent) e).memberId())
        .containsExactlyInAnyOrder(newMemberId1, newMemberId2);
  }

  private static LinkedApplicationGroupState stateAfterCreate(
      UUID groupId, UUID leadId, List<UUID> members) {
    LinkedApplicationGroupState state = new LinkedApplicationGroupState();
    LinkedApplicationGroupEvolve.apply(
        state, new LinkedApplicationGroupCreatedEvent(groupId, leadId, members, OCCURRED_AT));
    return state;
  }
}
