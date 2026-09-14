package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import java.util.HashSet;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationLinkConflictException;

/** Decision functions for linked-group commands. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LinkedApplicationGroupDecider {

  /** Establishes a linked group idempotently. */
  public static Optional<LinkedApplicationGroupCreatedEvent> decideEstablish(
      LinkedApplicationGroupState state, EstablishLinkedApplicationGroupCommand command) {
    if (state.groupId == null) {
      return Optional.of(
          new LinkedApplicationGroupCreatedEvent(
              command.groupId(),
              command.leadApplicationId(),
              command.memberApplicationIds(),
              command.occurredAt()));
    }

    if (!state.leadApplicationId.equals(command.leadApplicationId())) {
      throw new ApplicationLinkConflictException(
          "Linked application group "
              + state.groupId
              + " already exists with lead application "
              + state.leadApplicationId);
    }

    var currentMemberIds = new HashSet<>(state.memberApplicationIds);
    if (currentMemberIds.containsAll(command.memberApplicationIds())) {
      return Optional.empty();
    }

    var missingMemberId =
        command.memberApplicationIds().stream()
            .filter(memberApplicationId -> !currentMemberIds.contains(memberApplicationId))
            .findFirst()
            .orElseThrow();

    throw new ApplicationLinkConflictException(
        "Linked application group "
            + state.groupId
            + " does not contain requested member "
            + missingMemberId);
  }

  /** Adds one application to an established linked group idempotently. */
  public static Optional<MemberAddedToGroupEvent> decideAddApplication(
      LinkedApplicationGroupState state, AddApplicationToLinkedGroupCommand command) {
    if (state.groupId == null) {
      throw new IllegalStateException(
          "Linked application group " + command.groupId() + " has not been established");
    }
    if (state.memberApplicationIds.contains(command.applicationId())) {
      return Optional.empty();
    }

    return Optional.of(
        new MemberAddedToGroupEvent(
            state.groupId, state.leadApplicationId, command.applicationId(), command.occurredAt()));
  }
}
