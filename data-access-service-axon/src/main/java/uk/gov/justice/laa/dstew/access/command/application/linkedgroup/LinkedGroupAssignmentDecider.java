package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationDecider;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationState;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationLinkConflictException;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssignmentChange;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssignmentConflictException;
import uk.gov.justice.laa.dstew.access.command.worklist.assign.AssignLinkedGroupWorkItemCommand;
import uk.gov.justice.laa.dstew.access.command.worklist.unassign.UnassignLinkedGroupWorkItemCommand;

/** Pure business decisions for assignment changes involving linked applications. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LinkedGroupAssignmentDecider {

  /** Works out the shared assignment of the given members from their active members only. */
  public static GroupAssignment groupAssignment(
      UUID groupId, Collection<ApplicationState> members) {
    Set<UUID> caseworkerIds = new HashSet<>();
    for (var member : members) {
      if (ApplicationDecider.isActiveWorkItem(member)) {
        caseworkerIds.add(member.getCaseworkerId());
      }
    }
    if (caseworkerIds.isEmpty()) {
      return new GroupAssignment.None();
    }
    if (caseworkerIds.size() == 1) {
      var caseworkerId = caseworkerIds.iterator().next();
      return caseworkerId == null
          ? new GroupAssignment.Unassigned()
          : new GroupAssignment.Assigned(caseworkerId);
    }
    throw new ApplicationLinkConflictException(
        "Linked group "
            + groupId
            + " members do not share one assignment; unassign the linked group and assign it again");
  }

  /** Decides the assignment changes when applications join a group (DSTEW-2284 AC1-AC4). */
  public static List<WorkItemAssignmentChange> decideJoin(
      UUID groupId,
      Collection<ApplicationState> existingMembers,
      Collection<ApplicationState> joiningApplications,
      Instant occurredAt) {
    var existingAssignment = groupAssignment(groupId, existingMembers);
    UUID targetCaseworkerId =
        switch (existingAssignment) {
          case GroupAssignment.Assigned assigned -> assigned.caseworkerId();
          case GroupAssignment.Unassigned ignored ->
              firstAssignedJoiningCaseworker(joiningApplications);
          case GroupAssignment.None ignored -> firstAssignedJoiningCaseworker(joiningApplications);
        };
    if (targetCaseworkerId == null) {
      return List.of();
    }

    var allMembers = new TreeMap<UUID, ApplicationState>();
    existingMembers.forEach(state -> allMembers.put(state.getApplicationId(), state));
    joiningApplications.forEach(state -> allMembers.put(state.getApplicationId(), state));
    return decideMembersAssignment(allMembers.values(), targetCaseworkerId, occurredAt);
  }

  /**
   * Validates a group assign addressed through one member. {@code members} must hold every current
   * group member; check membership with {@link LinkedApplicationGroupDecider#validateMember} first.
   */
  public static void validateGroupAssign(
      SortedMap<UUID, ApplicationState> members, AssignLinkedGroupWorkItemCommand command) {
    ApplicationDecider.validateAssignable(
        members.get(command.workItemId()),
        command.workItemId(),
        command.expectedAssignmentVersion());
    for (var entry : members.entrySet()) {
      var memberId = entry.getKey();
      var state = entry.getValue();
      if (!memberId.equals(command.workItemId())
          && ApplicationDecider.isActiveWorkItem(state)
          && state.getCaseworkerId() != null
          && !state.getCaseworkerId().equals(command.caseworkerId())) {
        throw new WorkItemAssignmentConflictException(
            memberId,
            "it is assigned to caseworker "
                + state.getCaseworkerId()
                + "; unassign the linked group before assigning it");
      }
    }
  }

  /**
   * Validates a group unassign addressed through one member. {@code members} must hold every
   * current group member; check membership with {@link
   * LinkedApplicationGroupDecider#validateMember} first.
   */
  public static void validateGroupUnassign(
      SortedMap<UUID, ApplicationState> members, UnassignLinkedGroupWorkItemCommand command) {
    ApplicationDecider.validateUnassignable(
        members.get(command.workItemId()),
        command.workItemId(),
        command.expectedAssignmentVersion());
  }

  /**
   * Assigns every active member, addressed member first; call {@link #validateGroupAssign} first.
   */
  public static List<WorkItemAssignmentChange> decideGroupAssign(
      SortedMap<UUID, ApplicationState> members, AssignLinkedGroupWorkItemCommand command) {
    return decideMembersAssignment(
        addressedFirst(command.workItemId(), members),
        command.caseworkerId(),
        command.occurredAt());
  }

  /**
   * Unassigns every active member, addressed member first; call {@link #validateGroupUnassign}
   * first.
   */
  public static List<WorkItemAssignmentChange> decideGroupUnassign(
      SortedMap<UUID, ApplicationState> members, UnassignLinkedGroupWorkItemCommand command) {
    return decideMembersAssignment(
        addressedFirst(command.workItemId(), members), null, command.occurredAt());
  }

  /** Moves each active member, in the given order, to {@code targetCaseworkerId} (null = none). */
  private static List<WorkItemAssignmentChange> decideMembersAssignment(
      Collection<ApplicationState> membersInOrder,
      @Nullable UUID targetCaseworkerId,
      Instant occurredAt) {
    var events = new ArrayList<WorkItemAssignmentChange>();
    for (var state : membersInOrder) {
      ApplicationDecider.decideGroupMemberAssignment(state, targetCaseworkerId, occurredAt)
          .ifPresent(events::add);
    }
    return events;
  }

  private static List<ApplicationState> addressedFirst(
      UUID addressedId, SortedMap<UUID, ApplicationState> members) {
    var ordered = new ArrayList<ApplicationState>();
    ordered.add(members.get(addressedId));
    members.forEach(
        (memberId, state) -> {
          if (!memberId.equals(addressedId)) {
            ordered.add(state);
          }
        });
    return ordered;
  }

  /**
   * Decides the assignment of a grouped application that is being marked ready, from the state it
   * had before the readiness event. Empty means "keep the current assignment". The caller must
   * first check membership with {@link LinkedApplicationGroupDecider#validateMember}.
   */
  public static Optional<WorkItemAssignmentChange> decideReadyAssignment(
      UUID groupId,
      ApplicationState stateBeforeReady,
      long nextApplicationVersion,
      Collection<ApplicationState> otherMembers,
      Instant occurredAt) {
    var otherAssignment = groupAssignment(groupId, otherMembers);
    if (otherAssignment instanceof GroupAssignment.None) {
      return Optional.empty();
    }
    UUID targetCaseworkerId =
        otherAssignment instanceof GroupAssignment.Assigned(UUID caseworkerId)
            ? caseworkerId
            : null;
    return ApplicationDecider.decideAssignmentChange(
        stateBeforeReady.getApplicationId(),
        nextApplicationVersion,
        stateBeforeReady.getAssignmentVersion(),
        stateBeforeReady.getCaseworkerId(),
        targetCaseworkerId,
        occurredAt);
  }

  private static @Nullable UUID firstAssignedJoiningCaseworker(
      Collection<ApplicationState> joiningApplications) {
    var sortedApplications = new TreeMap<UUID, ApplicationState>();
    joiningApplications.forEach(state -> sortedApplications.put(state.getApplicationId(), state));
    return sortedApplications.values().stream()
        .filter(ApplicationDecider::isActiveWorkItem)
        .map(ApplicationState::getCaseworkerId)
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }
}
