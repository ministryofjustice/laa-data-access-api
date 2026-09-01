package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssignmentConflictException;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemId;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;

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

  /** Derives a member activation only when the durable route version still matches membership. */
  public static LinkedGroupMemberWorkItemChanged decideActivate(
      LinkedApplicationGroupState state, ActivateLinkedGroupMemberWorkItemCommand command) {
    validateMember(state, command.groupId(), command.applicationId(), command.expectedMembershipVersion());
    return new LinkedGroupMemberWorkItemChanged(
        state.groupId,
        command.applicationId(),
        true,
        state.membershipVersion,
        state.assignmentVersion,
        state.caseworkerId,
        command.occurredAt());
  }

  /** Derives a member removal, clearing the shared assignee when it was the final active member. */
  public static LinkedGroupMemberWorkItemChanged decideDeactivate(
      LinkedApplicationGroupState state, DeactivateLinkedGroupMemberWorkItemCommand command) {
    validateMember(state, command.groupId(), command.applicationId(), command.expectedMembershipVersion());
    boolean finalMember = state.activeMemberApplicationIds.size() == 1
        && state.activeMemberApplicationIds.contains(command.applicationId());
    return new LinkedGroupMemberWorkItemChanged(
        state.groupId,
        command.applicationId(),
        false,
        state.membershipVersion,
        state.assignmentVersion,
        finalMember ? null : state.caseworkerId,
        command.occurredAt());
  }

  /** Derives an immutable all-active-member assignment transition. */
  public static LinkedGroupAssigned decideAssign(
      LinkedApplicationGroupState state, AssignLinkedGroupWorkItemCommand command) {
    validateAssignableMember(
        state, command.groupId(), command.selectedApplicationId(), command.expectedMembershipVersion(),
        command.expectedAssignmentVersion());
    if (state.caseworkerId != null) {
      throw conflict(command.selectedApplicationId(), "the linked group is already assigned");
    }
    return new LinkedGroupAssigned(
        state.groupId,
        List.copyOf(state.activeMemberApplicationIds),
        state.membershipVersion,
        state.assignmentVersion + 1,
        command.caseworkerId(),
        command.occurredAt());
  }

  /** Derives an immutable all-active-member unassignment transition. */
  public static LinkedGroupUnassigned decideUnassign(
      LinkedApplicationGroupState state, UnassignLinkedGroupWorkItemCommand command) {
    validateAssignableMember(
        state, command.groupId(), command.selectedApplicationId(), command.expectedMembershipVersion(),
        command.expectedAssignmentVersion());
    if (state.caseworkerId == null) {
      throw conflict(command.selectedApplicationId(), "the linked group is already unassigned");
    }
    return new LinkedGroupUnassigned(
        state.groupId,
        List.copyOf(state.activeMemberApplicationIds),
        state.membershipVersion,
        state.assignmentVersion + 1,
        command.occurredAt());
  }

  private static void validateAssignableMember(
      LinkedApplicationGroupState state, java.util.UUID groupId, java.util.UUID applicationId,
      long membershipVersion, long assignmentVersion) {
    validateMember(state, groupId, applicationId, membershipVersion);
    if (!state.activeMemberApplicationIds.contains(applicationId)) {
      throw new ResourceNotFoundException("Linked-group work item is not active: " + applicationId);
    }
    if (assignmentVersion != state.assignmentVersion) {
      throw conflict(applicationId, "the assignment version is stale");
    }
  }

  private static void validateMember(
      LinkedApplicationGroupState state, java.util.UUID groupId, java.util.UUID applicationId,
      long membershipVersion) {
    if (state.groupId == null || !state.groupId.equals(groupId)
        || !state.memberApplicationIds.contains(applicationId)
        || membershipVersion != state.membershipVersion) {
      throw new ResourceNotFoundException("No current linked-group route found for " + applicationId);
    }
  }

  private static WorkItemAssignmentConflictException conflict(java.util.UUID applicationId, String reason) {
    return new WorkItemAssignmentConflictException(
        new WorkItemId(WorkItemType.APPLICATION, applicationId), reason);
  }
}
