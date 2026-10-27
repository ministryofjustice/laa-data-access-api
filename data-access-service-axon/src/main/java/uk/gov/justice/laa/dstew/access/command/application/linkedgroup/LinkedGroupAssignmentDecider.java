package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationDecider;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationState;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationLinkConflictException;
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
  public static List<Object> decideJoin(
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
    var events = new ArrayList<Object>();
    for (var state : allMembers.values()) {
      ApplicationDecider.decideGroupMemberAssignment(state, targetCaseworkerId, occurredAt)
          .ifPresent(events::add);
    }
    return events;
  }

  /**
   * Decides a group assign addressed through one member; throws when the request is not allowed.
   */
  public static List<Object> decideGroupAssign(
      LinkedApplicationGroupState group,
      Map<UUID, ApplicationState> members,
      AssignLinkedGroupWorkItemCommand command) {
    requireMember(group, command.workItemId());
    var addressed = members.get(command.workItemId());
    ApplicationDecider.validateDirectWorkItem(
        addressed, command.workItemId(), command.expectedAssignmentVersion());
    if (addressed.getCaseworkerId() != null) {
      throw new WorkItemAssignmentConflictException(command.workItemId(), "it is already assigned");
    }

    for (var entry : new TreeMap<>(members).entrySet()) {
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

    var events = new ArrayList<Object>();
    ApplicationDecider.decideGroupMemberAssignment(
            addressed, command.caseworkerId(), command.occurredAt())
        .ifPresent(events::add);
    for (var entry : new TreeMap<>(members).entrySet()) {
      if (!entry.getKey().equals(command.workItemId())) {
        ApplicationDecider.decideGroupMemberAssignment(
                entry.getValue(), command.caseworkerId(), command.occurredAt())
            .ifPresent(events::add);
      }
    }
    return events;
  }

  /**
   * Decides a group unassign addressed through one member; throws when the request is not allowed.
   */
  public static List<Object> decideGroupUnassign(
      LinkedApplicationGroupState group,
      Map<UUID, ApplicationState> members,
      UnassignLinkedGroupWorkItemCommand command) {
    requireMember(group, command.workItemId());
    var addressed = members.get(command.workItemId());
    ApplicationDecider.validateDirectWorkItem(
        addressed, command.workItemId(), command.expectedAssignmentVersion());
    if (addressed.getCaseworkerId() == null) {
      throw new WorkItemAssignmentConflictException(
          command.workItemId(), "it is already unassigned");
    }

    var events = new ArrayList<Object>();
    ApplicationDecider.decideGroupMemberAssignment(addressed, null, command.occurredAt())
        .ifPresent(events::add);
    for (var entry : new TreeMap<>(members).entrySet()) {
      if (!entry.getKey().equals(command.workItemId())) {
        ApplicationDecider.decideGroupMemberAssignment(entry.getValue(), null, command.occurredAt())
            .ifPresent(events::add);
      }
    }
    return events;
  }

  /**
   * Decides the assignment of a grouped application that is being marked ready, from the state it
   * had before the readiness event. Empty means "keep the current assignment".
   */
  public static Optional<Object> decideReadyAssignment(
      LinkedApplicationGroupState group,
      ApplicationState stateBeforeReady,
      long nextApplicationVersion,
      Collection<ApplicationState> otherMembers,
      Instant occurredAt) {
    requireMember(group, stateBeforeReady.getApplicationId());
    var otherAssignment = groupAssignment(group.getGroupId(), otherMembers);
    if (otherAssignment instanceof GroupAssignment.None) {
      return Optional.empty();
    }
    @Nullable UUID targetCaseworkerId =
        switch (otherAssignment) {
          case GroupAssignment.Assigned assigned -> assigned.caseworkerId();
          case GroupAssignment.Unassigned ignored -> null;
          case GroupAssignment.None ignored -> null;
        };
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
        .filter(caseworkerId -> caseworkerId != null)
        .findFirst()
        .orElse(null);
  }

  private static void requireMember(LinkedApplicationGroupState group, UUID applicationId) {
    if (group.getGroupId() == null
        || group.isDissolved()
        || group.getMemberApplicationIds() == null
        || !group.getMemberApplicationIds().contains(applicationId)) {
      throw new ApplicationLinkConflictException(
          "Linked group membership of application "
              + applicationId
              + " changed; retry the request");
    }
  }
}
