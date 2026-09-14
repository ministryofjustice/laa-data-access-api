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
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationLinkConflictException;

/** Pure unit tests for {@link LinkedApplicationGroupDecider} — no Spring, no Axon, no database. */
class LinkedApplicationGroupDeciderTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-07-15T08:00:00Z");
  private static final ObjectMapper OBJECT_MAPPER =
      JsonMapper.builder().findAndAddModules().build();

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
  void givenLegacyMemberAddedEventPayload_whenReplayed_thenLeadApplicationIdRemainsUnchanged()
      throws Exception {
    UUID groupId = UUID.randomUUID();
    UUID leadId = UUID.randomUUID();
    UUID existingMemberId = UUID.randomUUID();
    UUID newMemberId = UUID.randomUUID();
    LinkedApplicationGroupState state =
        stateAfterCreate(groupId, leadId, List.of(leadId, existingMemberId));

    MemberAddedToGroupEvent event =
        OBJECT_MAPPER.readValue(
            """
            {
              "groupId": "%s",
              "memberId": "%s",
              "occurredAt": "%s"
            }
            """
                .formatted(groupId, newMemberId, OCCURRED_AT),
            MemberAddedToGroupEvent.class);

    LinkedApplicationGroupEvolve.apply(state, event);

    assertThat(event.leadApplicationId()).isNull();
    assertThat(state.leadApplicationId).isEqualTo(leadId);
    assertThat(state.memberApplicationIds).containsExactly(leadId, existingMemberId, newMemberId);
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
