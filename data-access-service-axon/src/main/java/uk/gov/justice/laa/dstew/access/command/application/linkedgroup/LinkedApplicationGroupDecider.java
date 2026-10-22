package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.AssignLinkedGroupCommand;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.DelegatedAssignment;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.InheritLinkedGroupAssignmentCommand;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.LinkedGroupAssignedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.LinkedGroupMemberAssignmentInheritedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.LinkedGroupUnassignedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.UnassignLinkedGroupCommand;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.ValidateLinkedGroupAssigneeCommand;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationLinkConflictException;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssignmentConflictException;
import uk.gov.justice.laa.dstew.access.exception.LinkedApplicationGroupVersionConflictException;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

/** Decision functions for linked-group commands. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LinkedApplicationGroupDecider {

  /** Establishes a linked group idempotently. */
  public static List<Object> decideEstablish(
      LinkedApplicationGroupState state, EstablishLinkedApplicationGroupCommand command) {
    if (state.groupId == null) {
      var leadCaseworkerId =
          command.joiningAssignments().get(command.leadApplicationId()).caseworkerId();
      var joiningCaseworkerId =
          command.memberApplicationIds().stream()
              .filter(
                  memberApplicationId -> !memberApplicationId.equals(command.leadApplicationId()))
              .map(memberApplicationId -> command.joiningAssignments().get(memberApplicationId))
              .map(DelegatedAssignment::caseworkerId)
              .filter(Objects::nonNull)
              .findFirst()
              .orElse(null);
      var groupCaseworkerId = resolveGroupCaseworker(leadCaseworkerId, joiningCaseworkerId);
      var events = new ArrayList<Object>();
      events.add(
          new LinkedApplicationGroupCreatedEvent(
              command.groupId(),
              command.leadApplicationId(),
              command.memberApplicationIds(),
              command.occurredAt()));
      if (groupCaseworkerId != null) {
        events.add(
            new LinkedGroupAssignedEvent(
                command.groupId(), groupCaseworkerId, 1, command.occurredAt()));
      }
      return List.copyOf(events);
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
      return List.of();
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
  public static List<Object> decideAddApplication(
      LinkedApplicationGroupState state, AddApplicationToLinkedGroupCommand command) {
    if (state.groupId == null) {
      throw new IllegalStateException(
          "Linked application group " + command.groupId() + " has not been established");
    }
    if (state.dissolved) {
      throw dissolvedConflict(state.groupId);
    }
    if (state.memberApplicationIds.contains(command.applicationId())) {
      return List.of();
    }
    if (command.expectedGroupVersion() != state.groupVersion) {
      throw LinkedApplicationGroupVersionConflictException.groupVersionMismatch();
    }

    var events = new ArrayList<Object>();
    events.add(
        new MemberAddedToGroupEvent(
            state.groupId, state.leadApplicationId, command.applicationId(), command.occurredAt()));
    var groupCaseworkerId =
        resolveGroupCaseworker(state.caseworkerId, command.memberAssignment().caseworkerId());
    if (!Objects.equals(groupCaseworkerId, state.caseworkerId)) {
      events.add(
          new LinkedGroupAssignedEvent(
              state.groupId, groupCaseworkerId, state.assignmentVersion + 1, command.occurredAt()));
    }
    return List.copyOf(events);
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
  public static LinkedGroupMemberRemoval decideRemoveApplication(
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
      return new LinkedApplicationGroupDissolvedEvent(
          state.groupId,
          state.leadApplicationId,
          command.applicationId(),
          List.copyOf(state.memberApplicationIds),
          nextVersion,
          state.caseworkerId,
          state.assignmentVersion,
          command.occurredAt());
    }

    return new MemberRemovedFromGroupEvent(
        state.groupId,
        state.leadApplicationId,
        command.applicationId(),
        nextVersion,
        state.caseworkerId,
        state.assignmentVersion,
        command.occurredAt());
  }

  /**
   * AC1–AC4: the lead side keeps its caseworker; otherwise the group adopts the joining caseworker.
   */
  public static @Nullable UUID resolveGroupCaseworker(
      @Nullable UUID leadSideCaseworkerId, @Nullable UUID joiningCaseworkerId) {
    return leadSideCaseworkerId != null ? leadSideCaseworkerId : joiningCaseworkerId;
  }

  /** Returns the removal result with leaving application identifiers in ascending order. */
  public static LinkedGroupRemovalResult removalResult(LinkedGroupMemberRemoval removal) {
    if (removal instanceof LinkedApplicationGroupDissolvedEvent dissolved) {
      return new LinkedGroupRemovalResult(
          dissolved.groupId(),
          dissolved.memberApplicationIds().stream().sorted().toList(),
          dissolved.caseworkerId(),
          dissolved.assignmentVersion());
    }
    var removed = (MemberRemovedFromGroupEvent) removal;
    return new LinkedGroupRemovalResult(
        removed.groupId(),
        List.of(removed.memberId()),
        removed.caseworkerId(),
        removed.assignmentVersion());
  }

  /**
   * Assigns the group when the addressed application is still a member and the version is current.
   */
  public static LinkedGroupAssignedEvent decideAssign(
      LinkedApplicationGroupState state, AssignLinkedGroupCommand command) {
    requireMember(state, command.applicationId());
    if (command.expectedAssignmentVersion() != state.assignmentVersion) {
      throw new WorkItemAssignmentConflictException(
          command.applicationId(), "the assignment version is stale");
    }
    if (state.caseworkerId != null) {
      throw new WorkItemAssignmentConflictException(
          command.applicationId(), "it is already assigned");
    }
    return new LinkedGroupAssignedEvent(
        state.groupId, command.caseworkerId(), state.assignmentVersion + 1, command.occurredAt());
  }

  /**
   * Clears the group assignment when the addressed application is still a member and the version is
   * current.
   */
  public static LinkedGroupUnassignedEvent decideUnassign(
      LinkedApplicationGroupState state, UnassignLinkedGroupCommand command) {
    requireMember(state, command.applicationId());
    if (command.expectedAssignmentVersion() != state.assignmentVersion) {
      throw new WorkItemAssignmentConflictException(
          command.applicationId(), "the assignment version is stale");
    }
    if (state.caseworkerId == null) {
      throw new WorkItemAssignmentConflictException(
          command.applicationId(), "it is already unassigned");
    }
    return new LinkedGroupUnassignedEvent(
        state.groupId, state.assignmentVersion + 1, command.occurredAt());
  }

  /** Records an inherited assignment for a ready member without changing the group assignment. */
  public static Optional<LinkedGroupMemberAssignmentInheritedEvent> decideInherit(
      LinkedApplicationGroupState state, InheritLinkedGroupAssignmentCommand command) {
    if (state.groupId == null
        || state.dissolved
        || state.memberApplicationIds == null
        || !state.memberApplicationIds.contains(command.applicationId())) {
      throw new ApplicationLinkConflictException(
          "Application "
              + command.applicationId()
              + " is not a member of linked application group "
              + command.groupId());
    }
    if (state.caseworkerId == null) {
      return Optional.empty();
    }
    return Optional.of(
        new LinkedGroupMemberAssignmentInheritedEvent(
            state.groupId,
            command.applicationId(),
            state.caseworkerId,
            state.assignmentVersion,
            command.occurredAt()));
  }

  /** Validates that the supplied caseworker currently owns the group assignment. */
  public static void validateAssignee(
      LinkedApplicationGroupState state, ValidateLinkedGroupAssigneeCommand command) {
    requireMember(state, command.applicationId());
    if (state.caseworkerId == null) {
      throw new WorkItemAssignmentConflictException(command.applicationId(), "it is unassigned");
    }
    if (!state.caseworkerId.equals(command.caseworkerId())) {
      throw new WorkItemAssignmentConflictException(
          command.applicationId(), "the supplied caseworker is not its current assignee");
    }
  }

  private static void requireMember(LinkedApplicationGroupState state, UUID applicationId) {
    if (state.groupId == null
        || state.dissolved
        || state.memberApplicationIds == null
        || !state.memberApplicationIds.contains(applicationId)) {
      throw new WorkItemAssignmentConflictException(
          applicationId, "it is no longer a member of its linked group");
    }
  }

  /** Validates that an application may create a Prior Authority against the current group state. */
  public static void validatePriorAuthorityGroupEligibility(
      LinkedApplicationGroupState state, ValidatePriorAuthorityGroupEligibilityCommand command) {
    if (state.groupId == null
        || state.dissolved
        || !state.memberApplicationIds.contains(command.applicationId())) {
      throw LinkedApplicationGroupVersionConflictException.groupChanged(command.applicationId());
    }
    if (!state.leadApplicationId.equals(command.applicationId())) {
      throw new ValidationException(
          List.of(
              "Prior Authority applications can only be created against a lead or standalone"
                  + " application"));
    }
  }

  private static ApplicationLinkConflictException dissolvedConflict(UUID groupId) {
    return new ApplicationLinkConflictException(
        "Linked application group " + groupId + " has been dissolved");
  }
}
