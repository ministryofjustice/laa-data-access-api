package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Decision functions for linked-group commands. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LinkedApplicationGroupDecider {

  /**
   * Initialises the group or adds new members idempotently.
   *
   * <p>When the group does not yet exist, validates that the lead is in the member list and returns
   * a singleton {@link LinkedApplicationGroupCreatedEvent}. When the group already exists, diffs
   * the incoming member list and returns a {@link MemberAddedToGroupEvent} for each new member;
   * returns an empty list if all members are already present.
   */
  public static List<Object> decideInitialise(
      LinkedApplicationGroupState state, InitialiseLinkedApplicationGroupCommand command) {

    if (state.groupId != null) {
      return command.memberApplicationIds().stream()
          .filter(id -> !state.memberApplicationIds.contains(id))
          .map(id -> (Object) new MemberAddedToGroupEvent(state.groupId, id, command.occurredAt()))
          .toList();
    }

    if (!command.memberApplicationIds().contains(command.leadApplicationId())) {
      throw new IllegalArgumentException(
          "Lead application "
              + command.leadApplicationId()
              + " must be present in the member list");
    }

    return List.of(
        new LinkedApplicationGroupCreatedEvent(
            command.groupId(),
            command.leadApplicationId(),
            List.copyOf(command.memberApplicationIds()),
            command.occurredAt()));
  }
}
