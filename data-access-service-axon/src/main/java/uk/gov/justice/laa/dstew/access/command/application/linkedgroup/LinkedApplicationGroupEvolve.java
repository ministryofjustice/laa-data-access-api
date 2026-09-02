package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import java.util.ArrayList;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Event-fold functions for {@link LinkedApplicationGroupState}. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LinkedApplicationGroupEvolve {

  /** Applies a {@link LinkedApplicationGroupCreatedEvent} to the given state. */
  public static void apply(
      LinkedApplicationGroupState state, LinkedApplicationGroupCreatedEvent event) {
    state.groupId = event.groupId();
    state.leadApplicationId = event.leadApplicationId();
    state.memberApplicationIds = new ArrayList<>(event.memberApplicationIds());
    state.membershipVersion = 1L;
  }

  /** Applies a {@link MemberAddedToGroupEvent} to the given state. */
  public static void apply(LinkedApplicationGroupState state, MemberAddedToGroupEvent event) {
    state.memberApplicationIds.add(event.memberId());
    state.membershipVersion++;
  }

  /** Applies one member's eligibility change and any required final-member assignee clear. */
  public static void apply(
      LinkedApplicationGroupState state, LinkedGroupMemberWorkItemChanged event) {
    if (event.active()) {
      state.activeMemberApplicationIds.add(event.applicationId());
    } else {
      state.activeMemberApplicationIds.remove(event.applicationId());
    }
  }

}
