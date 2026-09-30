package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationLinkConflictException;
import uk.gov.justice.laa.dstew.access.exception.LinkedApplicationGroupVersionConflictException;

/** Pure unit tests for {@link LinkedApplicationGroupDecider} — no Spring, no Axon, no database. */
class LinkedApplicationGroupDeciderTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-07-15T08:00:00Z");

  @Test
  void givenNoGroup_whenDecideEstablish_thenReturnsGroupCreatedEvent() {
    LinkedApplicationGroupState state = new LinkedApplicationGroupState();
    UUID groupId = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();
    UUID sourceApplicationId = UUID.randomUUID();
    List<UUID> members = List.of(leadId, sourceApplicationId);

    LinkedApplicationGroupCreatedEvent event =
        LinkedApplicationGroupDecider.decideEstablish(
                state,
                new EstablishLinkedApplicationGroupCommand(groupId, leadId, members, OCCURRED_AT))
            .orElseThrow();

    assertThat(event.groupId()).isEqualTo(groupId);
    assertThat(event.leadApplicationId()).isEqualTo(leadId);
    assertThat(event.memberApplicationIds()).containsExactlyElementsOf(members);
    assertThat(event.occurredAt()).isEqualTo(OCCURRED_AT);
  }

  @Test
  void givenMutableMemberList_whenConstructingEstablishCommand_thenDefensivelyCopiesMembers() {
    UUID groupId = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();
    ArrayList<UUID> mutableMembers = new ArrayList<>(List.of(leadId, UUID.randomUUID()));

    EstablishLinkedApplicationGroupCommand command =
        new EstablishLinkedApplicationGroupCommand(groupId, leadId, mutableMembers, OCCURRED_AT);
    mutableMembers.add(UUID.randomUUID());

    assertThat(command.memberApplicationIds()).hasSize(2);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidEstablishCommands")
  void givenInvalidEstablishInput_whenDecideEstablish_thenThrowsIllegalArgument(
      String scenario, List<UUID> memberApplicationIds) {
    LinkedApplicationGroupState state = new LinkedApplicationGroupState();
    UUID groupId = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();

    assertThatThrownBy(
            () ->
                LinkedApplicationGroupDecider.decideEstablish(
                    state,
                    new EstablishLinkedApplicationGroupCommand(
                        groupId, leadId, memberApplicationIds, OCCURRED_AT)))
        .as(scenario)
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void
      givenExistingGroup_whenEquivalentEstablishRetriedInDifferentOrder_thenReturnsEmptyOptional() {
    UUID groupId = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();
    UUID sourceApplicationId = UUID.randomUUID();
    LinkedApplicationGroupState state =
        stateAfterCreate(groupId, leadId, List.of(leadId, sourceApplicationId));

    var decision =
        LinkedApplicationGroupDecider.decideEstablish(
            state,
            new EstablishLinkedApplicationGroupCommand(
                groupId, leadId, List.of(sourceApplicationId, leadId), OCCURRED_AT));

    assertThat(decision).isEmpty();
  }

  @Test
  void
      givenExistingGroupWithLaterMemberAddition_whenOriginalEstablishRetried_thenReturnsEmptyOptional() {
    UUID groupId = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();
    UUID sourceApplicationId = UUID.randomUUID();
    UUID laterMemberId = UUID.randomUUID();
    LinkedApplicationGroupState state =
        stateAfterMemberAdded(groupId, leadId, List.of(leadId, sourceApplicationId), laterMemberId);

    var decision =
        LinkedApplicationGroupDecider.decideEstablish(
            state,
            new EstablishLinkedApplicationGroupCommand(
                groupId, leadId, List.of(leadId, sourceApplicationId), OCCURRED_AT));

    assertThat(decision).isEmpty();
  }

  @Test
  void givenExistingGroup_whenEstablishUsesDifferentLead_thenThrowsConflict() {
    UUID groupId = UUID.randomUUID();
    UUID currentLeadId = UUID.randomUUID();
    UUID differentLeadId = UUID.randomUUID();
    UUID sourceApplicationId = UUID.randomUUID();
    LinkedApplicationGroupState state =
        stateAfterCreate(groupId, currentLeadId, List.of(currentLeadId, sourceApplicationId));

    assertThatThrownBy(
            () ->
                LinkedApplicationGroupDecider.decideEstablish(
                    state,
                    new EstablishLinkedApplicationGroupCommand(
                        groupId,
                        differentLeadId,
                        List.of(differentLeadId, sourceApplicationId),
                        OCCURRED_AT)))
        .isInstanceOf(ApplicationLinkConflictException.class)
        .hasMessageContaining(currentLeadId.toString())
        .hasMessageContaining(differentLeadId.toString())
        .hasMessageNotContaining(groupId.toString());
  }

  @Test
  void givenExistingGroup_whenEstablishRequestsMemberMissingFromState_thenThrowsConflict() {
    UUID groupId = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();
    UUID sourceApplicationId = UUID.randomUUID();
    UUID missingMemberId = UUID.randomUUID();
    LinkedApplicationGroupState state =
        stateAfterCreate(groupId, leadId, List.of(leadId, sourceApplicationId));

    assertThatThrownBy(
            () ->
                LinkedApplicationGroupDecider.decideEstablish(
                    state,
                    new EstablishLinkedApplicationGroupCommand(
                        groupId,
                        leadId,
                        List.of(leadId, sourceApplicationId, missingMemberId),
                        OCCURRED_AT)))
        .isInstanceOf(ApplicationLinkConflictException.class)
        .hasMessageContaining(missingMemberId.toString())
        .hasMessageNotContaining(groupId.toString());
  }

  @Test
  void givenExistingGroup_whenDecideAddApplication_thenReturnsMemberAddedEventWithCurrentLead() {
    UUID groupId = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();
    UUID existingMemberId = UUID.randomUUID();
    UUID newMemberId = UUID.randomUUID();
    LinkedApplicationGroupState state =
        stateAfterCreate(groupId, leadId, List.of(leadId, existingMemberId));

    MemberAddedToGroupEvent event =
        LinkedApplicationGroupDecider.decideAddApplication(
                state, new AddApplicationToLinkedGroupCommand(groupId, newMemberId, OCCURRED_AT))
            .orElseThrow();

    assertThat(event.groupId()).isEqualTo(groupId);
    assertThat(event.leadApplicationId()).isEqualTo(leadId);
    assertThat(event.memberId()).isEqualTo(newMemberId);
    assertThat(event.occurredAt()).isEqualTo(OCCURRED_AT);
  }

  @Test
  void givenExistingGroup_whenApplicationAlreadyAMember_thenAddReturnsEmptyOptional() {
    UUID groupId = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();
    UUID existingMemberId = UUID.randomUUID();
    LinkedApplicationGroupState state =
        stateAfterCreate(groupId, leadId, List.of(leadId, existingMemberId));

    var decision =
        LinkedApplicationGroupDecider.decideAddApplication(
            state, new AddApplicationToLinkedGroupCommand(groupId, existingMemberId, OCCURRED_AT));

    assertThat(decision).isEmpty();
  }

  @Test
  void givenNoGroup_whenDecideAddApplication_thenThrowsIllegalState() {
    LinkedApplicationGroupState state = new LinkedApplicationGroupState();

    assertThatThrownBy(
            () ->
                LinkedApplicationGroupDecider.decideAddApplication(
                    state,
                    new AddApplicationToLinkedGroupCommand(
                        UUID.randomUUID(), UUID.randomUUID(), OCCURRED_AT)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void givenMemberAddedEventApplied_whenStateEvolved_thenLeadApplicationIdRemainsUnchanged() {
    UUID groupId = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();
    UUID existingMemberId = UUID.randomUUID();
    UUID newMemberId = UUID.randomUUID();
    LinkedApplicationGroupState state =
        stateAfterCreate(groupId, leadId, List.of(leadId, existingMemberId));

    LinkedApplicationGroupEvolve.apply(
        state, new MemberAddedToGroupEvent(groupId, leadId, newMemberId, OCCURRED_AT));

    assertThat(state.leadApplicationId).isEqualTo(leadId);
    assertThat(state.memberApplicationIds).containsExactly(leadId, existingMemberId, newMemberId);
  }

  @Test
  void givenAssociate_whenDecideChangeLead_thenReturnsVersionedEvent() {
    UUID groupId = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();
    UUID associateId = UUID.randomUUID();
    LinkedApplicationGroupState state =
        stateAfterCreate(groupId, leadId, List.of(leadId, associateId));

    var event =
        LinkedApplicationGroupDecider.decideChangeLead(
                state, new ChangeLinkedGroupLeadCommand(groupId, associateId, 0, OCCURRED_AT))
            .orElseThrow();

    assertThat(event)
        .isEqualTo(
            new LinkedApplicationGroupLeadChangedEvent(
                groupId, leadId, associateId, 1, OCCURRED_AT));
  }

  @Test
  void givenCurrentLeadAndStaleVersion_whenDecideChangeLead_thenReturnsEmpty() {
    UUID groupId = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();
    LinkedApplicationGroupState state =
        stateAfterCreate(groupId, leadId, List.of(leadId, UUID.randomUUID()));

    assertThat(
            LinkedApplicationGroupDecider.decideChangeLead(
                state, new ChangeLinkedGroupLeadCommand(groupId, leadId, 42, OCCURRED_AT)))
        .isEmpty();
  }

  @Test
  void givenNoGroup_whenDecideChangeLead_thenThrowsIllegalState() {
    UUID groupId = UUID.randomUUID();

    assertThatThrownBy(
            () ->
                LinkedApplicationGroupDecider.decideChangeLead(
                    new LinkedApplicationGroupState(),
                    new ChangeLinkedGroupLeadCommand(groupId, UUID.randomUUID(), 0, OCCURRED_AT)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Linked application group " + groupId + " has not been established");
  }

  @Test
  void givenDissolvedGroup_whenDecideChangeLead_thenThrowsConflict() {
    UUID groupId = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();
    UUID associateId = UUID.randomUUID();
    LinkedApplicationGroupState state =
        stateAfterCreate(groupId, leadId, List.of(leadId, associateId));
    state.dissolved = true;

    assertThatThrownBy(
            () ->
                LinkedApplicationGroupDecider.decideChangeLead(
                    state, new ChangeLinkedGroupLeadCommand(groupId, associateId, 0, OCCURRED_AT)))
        .isInstanceOf(ApplicationLinkConflictException.class)
        .hasMessage("Linked application group " + groupId + " has been dissolved");
  }

  @Test
  void givenNonMember_whenDecideChangeLead_thenThrowsConflict() {
    UUID groupId = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();
    UUID nonMemberId = UUID.randomUUID();
    LinkedApplicationGroupState state =
        stateAfterCreate(groupId, leadId, List.of(leadId, UUID.randomUUID()));

    assertThatThrownBy(
            () ->
                LinkedApplicationGroupDecider.decideChangeLead(
                    state, new ChangeLinkedGroupLeadCommand(groupId, nonMemberId, 0, OCCURRED_AT)))
        .isInstanceOf(ApplicationLinkConflictException.class)
        .hasMessage(
            "Application "
                + nonMemberId
                + " is not a member of linked application group "
                + groupId);
  }

  @Test
  void givenStaleVersion_whenDecideChangeLead_thenThrowsVersionConflict() {
    UUID groupId = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();
    UUID associateId = UUID.randomUUID();
    LinkedApplicationGroupState state =
        stateAfterCreate(groupId, leadId, List.of(leadId, associateId));

    assertThatThrownBy(
            () ->
                LinkedApplicationGroupDecider.decideChangeLead(
                    state, new ChangeLinkedGroupLeadCommand(groupId, associateId, 1, OCCURRED_AT)))
        .isInstanceOf(LinkedApplicationGroupVersionConflictException.class)
        .hasMessage("Linked group of application " + associateId + " has changed since version 1");
  }

  @Test
  void givenThreeMembers_whenDecideRemoveAssociate_thenReturnsVersionedRemovalEvent() {
    UUID groupId = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();
    UUID removedId = UUID.randomUUID();
    UUID remainingId = UUID.randomUUID();
    LinkedApplicationGroupState state =
        stateAfterCreate(groupId, leadId, List.of(leadId, removedId, remainingId));

    var event =
        LinkedApplicationGroupDecider.decideRemoveApplication(
                state,
                new RemoveApplicationFromLinkedGroupCommand(groupId, removedId, 0, OCCURRED_AT))
            .orElseThrow();

    assertThat(event)
        .isEqualTo(new MemberRemovedFromGroupEvent(groupId, leadId, removedId, 1, OCCURRED_AT));
  }

  @Test
  void givenTwoMembers_whenDecideRemoveAssociate_thenReturnsDissolvedEvent() {
    UUID groupId = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();
    UUID removedId = UUID.randomUUID();
    List<UUID> members = List.of(leadId, removedId);
    LinkedApplicationGroupState state = stateAfterCreate(groupId, leadId, members);

    var event =
        LinkedApplicationGroupDecider.decideRemoveApplication(
                state,
                new RemoveApplicationFromLinkedGroupCommand(groupId, removedId, 0, OCCURRED_AT))
            .orElseThrow();

    assertThat(event)
        .isEqualTo(
            new LinkedApplicationGroupDissolvedEvent(
                groupId, leadId, removedId, members, 1, OCCURRED_AT));
  }

  @Test
  void givenLead_whenDecideRemoveApplication_thenThrowsConflict() {
    UUID groupId = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();
    LinkedApplicationGroupState state =
        stateAfterCreate(groupId, leadId, List.of(leadId, UUID.randomUUID()));

    assertThatThrownBy(
            () ->
                LinkedApplicationGroupDecider.decideRemoveApplication(
                    state,
                    new RemoveApplicationFromLinkedGroupCommand(groupId, leadId, 0, OCCURRED_AT)))
        .isInstanceOf(ApplicationLinkConflictException.class)
        .hasMessage(
            "Application "
                + leadId
                + " is the lead of its linked group; change the lead before removing it");
  }

  @Test
  void givenStaleVersion_whenDecideRemoveApplication_thenThrowsVersionConflictBeforeLeadCheck() {
    UUID groupId = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();
    LinkedApplicationGroupState state =
        stateAfterCreate(groupId, leadId, List.of(leadId, UUID.randomUUID()));

    assertThatThrownBy(
            () ->
                LinkedApplicationGroupDecider.decideRemoveApplication(
                    state,
                    new RemoveApplicationFromLinkedGroupCommand(groupId, leadId, 1, OCCURRED_AT)))
        .isInstanceOf(LinkedApplicationGroupVersionConflictException.class);
  }

  @Test
  void givenNoGroup_whenDecideRemoveApplication_thenThrowsIllegalState() {
    UUID groupId = UUID.randomUUID();

    assertThatThrownBy(
            () ->
                LinkedApplicationGroupDecider.decideRemoveApplication(
                    new LinkedApplicationGroupState(),
                    new RemoveApplicationFromLinkedGroupCommand(
                        groupId, UUID.randomUUID(), 0, OCCURRED_AT)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Linked application group " + groupId + " has not been established");
  }

  @Test
  void givenDissolvedGroup_whenDecideRemoveApplication_thenThrowsConflict() {
    UUID groupId = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();
    UUID associateId = UUID.randomUUID();
    LinkedApplicationGroupState state =
        stateAfterCreate(groupId, leadId, List.of(leadId, associateId));
    state.dissolved = true;

    assertThatThrownBy(
            () ->
                LinkedApplicationGroupDecider.decideRemoveApplication(
                    state,
                    new RemoveApplicationFromLinkedGroupCommand(
                        groupId, associateId, 0, OCCURRED_AT)))
        .isInstanceOf(ApplicationLinkConflictException.class)
        .hasMessage("Linked application group " + groupId + " has been dissolved");
  }

  @Test
  void givenNonMember_whenDecideRemoveApplication_thenThrowsConflict() {
    UUID groupId = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();
    UUID nonMemberId = UUID.randomUUID();
    LinkedApplicationGroupState state =
        stateAfterCreate(groupId, leadId, List.of(leadId, UUID.randomUUID()));

    assertThatThrownBy(
            () ->
                LinkedApplicationGroupDecider.decideRemoveApplication(
                    state,
                    new RemoveApplicationFromLinkedGroupCommand(
                        groupId, nonMemberId, 0, OCCURRED_AT)))
        .isInstanceOf(ApplicationLinkConflictException.class)
        .hasMessage(
            "Application "
                + nonMemberId
                + " is not a member of linked application group "
                + groupId);
  }

  @Test
  void givenDissolvedGroup_whenDecideAddApplication_thenThrowsConflict() {
    UUID groupId = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();
    LinkedApplicationGroupState state =
        stateAfterCreate(groupId, leadId, List.of(leadId, UUID.randomUUID()));
    state.dissolved = true;

    assertThatThrownBy(
            () ->
                LinkedApplicationGroupDecider.decideAddApplication(
                    state,
                    new AddApplicationToLinkedGroupCommand(
                        groupId, UUID.randomUUID(), OCCURRED_AT)))
        .isInstanceOf(ApplicationLinkConflictException.class)
        .hasMessage("Linked application group " + groupId + " has been dissolved");
  }

  @Test
  void givenDissolvedGroup_whenDecideEstablish_thenThrowsConflict() {
    UUID groupId = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();
    LinkedApplicationGroupState state =
        stateAfterCreate(groupId, leadId, List.of(leadId, UUID.randomUUID()));
    state.dissolved = true;

    assertThatThrownBy(
            () ->
                LinkedApplicationGroupDecider.decideEstablish(
                    state,
                    new EstablishLinkedApplicationGroupCommand(
                        groupId, leadId, List.of(leadId), OCCURRED_AT)))
        .isInstanceOf(ApplicationLinkConflictException.class)
        .hasMessage("Linked application group " + groupId + " has been dissolved");
  }

  private static LinkedApplicationGroupState stateAfterCreate(
      UUID groupId, UUID leadId, List<UUID> members) {
    LinkedApplicationGroupState state = new LinkedApplicationGroupState();
    LinkedApplicationGroupEvolve.apply(
        state, new LinkedApplicationGroupCreatedEvent(groupId, leadId, members, OCCURRED_AT));
    return state;
  }

  private static LinkedApplicationGroupState stateAfterMemberAdded(
      UUID groupId, UUID leadId, List<UUID> initialMembers, UUID newMemberId) {
    LinkedApplicationGroupState state = stateAfterCreate(groupId, leadId, initialMembers);
    LinkedApplicationGroupEvolve.apply(
        state, new MemberAddedToGroupEvent(groupId, leadId, newMemberId, OCCURRED_AT));
    return state;
  }

  private static List<Arguments> invalidEstablishCommands() {
    UUID leadId = UUID.randomUUID();
    UUID otherMemberId = UUID.randomUUID();
    return List.of(
        Arguments.of("empty member list", List.of()),
        Arguments.of("missing lead application", List.of(otherMemberId)),
        Arguments.of("duplicate member ids", List.of(leadId, otherMemberId, otherMemberId)));
  }
}
