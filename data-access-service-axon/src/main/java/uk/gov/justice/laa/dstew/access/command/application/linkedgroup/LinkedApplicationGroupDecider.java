package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationLinkConflictException;
import uk.gov.justice.laa.dstew.access.exception.LinkedApplicationGroupVersionConflictException;

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

    if (state.dissolved) {
      throw dissolvedConflict(state.groupId);
    }

    if (!state.leadApplicationId.equals(command.leadApplicationId())) {
      throw new ApplicationLinkConflictException(
          "Requested lead application "
              + command.leadApplicationId()
              + " conflicts with existing lead application "
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
        "Requested application "
            + missingMemberId
            + " is not part of the established linked application group");
  }

  /** Adds one application to an established linked group idempotently. */
  public static Optional<MemberAddedToGroupEvent> decideAddApplication(
      LinkedApplicationGroupState state, AddApplicationToLinkedGroupCommand command) {
    if (state.groupId == null) {
      throw new IllegalStateException(
          "Linked application group " + command.groupId() + " has not been established");
    }
    if (state.dissolved) {
      throw dissolvedConflict(state.groupId);
    }
    if (state.memberApplicationIds.contains(command.applicationId())) {
      return Optional.empty();
    }
    if (command.expectedGroupVersion() != state.groupVersion) {
      throw new LinkedApplicationGroupVersionConflictException(command.applicationId());
    }

    return Optional.of(
        new MemberAddedToGroupEvent(
            state.groupId, state.leadApplicationId, command.applicationId(), command.occurredAt()));
  }

  /** Changes the group's lead using its current aggregate version. */
  public static Optional<LinkedApplicationGroupLeadChangedEvent> decideChangeLead(
      LinkedApplicationGroupState state, ChangeLinkedGroupLeadCommand command) {
    if (state.groupId == null) {
      throw new IllegalStateException(
          "Linked application group " + command.groupId() + " has not been established");
    }
    if (state.dissolved) {
      throw dissolvedConflict(state.groupId);
    }
    if (!state.memberApplicationIds.contains(command.newLeadApplicationId())) {
      throw new ApplicationLinkConflictException(
          "Application "
              + command.newLeadApplicationId()
              + " is not a member of linked application group "
              + state.groupId);
    }
    if (command.newLeadApplicationId().equals(state.leadApplicationId)) {
      return Optional.empty();
    }
    if (command.expectedGroupVersion() != state.groupVersion) {
      throw new LinkedApplicationGroupVersionConflictException(command.newLeadApplicationId());
    }

    return Optional.of(
        new LinkedApplicationGroupLeadChangedEvent(
            state.groupId,
            state.leadApplicationId,
            command.newLeadApplicationId(),
            state.groupVersion + 1,
            command.occurredAt()));
  }

  /** Removes a non-lead member, dissolving a two-member group. */
  public static Optional<LinkedGroupMemberRemoval> decideRemoveApplication(
      LinkedApplicationGroupState state, RemoveApplicationFromLinkedGroupCommand command) {
    if (state.groupId == null) {
      throw new IllegalStateException(
          "Linked application group " + command.groupId() + " has not been established");
    }
    if (state.dissolved) {
      throw dissolvedConflict(state.groupId);
    }
    if (!state.memberApplicationIds.contains(command.applicationId())) {
      throw new ApplicationLinkConflictException(
          "Application "
              + command.applicationId()
              + " is not a member of linked application group "
              + state.groupId);
    }
    if (command.expectedGroupVersion() != state.groupVersion) {
      throw new LinkedApplicationGroupVersionConflictException(command.applicationId());
    }
    if (command.applicationId().equals(state.leadApplicationId)) {
      throw new ApplicationLinkConflictException(
          "Application "
              + command.applicationId()
              + " is the lead of its linked group; change the lead before removing it");
    }

    var nextVersion = state.groupVersion + 1;
    if (state.memberApplicationIds.size() == 2) {
      return Optional.of(
          new LinkedApplicationGroupDissolvedEvent(
              state.groupId,
              state.leadApplicationId,
              command.applicationId(),
              List.copyOf(state.memberApplicationIds),
              nextVersion,
              command.occurredAt()));
    }

    return Optional.of(
        new MemberRemovedFromGroupEvent(
            state.groupId,
            state.leadApplicationId,
            command.applicationId(),
            nextVersion,
            command.occurredAt()));
  }

  private static ApplicationLinkConflictException dissolvedConflict(UUID groupId) {
    return new ApplicationLinkConflictException(
        "Linked application group " + groupId + " has been dissolved");
  }
}
