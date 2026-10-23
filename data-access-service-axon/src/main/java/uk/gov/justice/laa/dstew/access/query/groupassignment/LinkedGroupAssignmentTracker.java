package uk.gov.justice.laa.dstew.access.query.groupassignment;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** Applies linked-group assignment facts to one projection's tracking store. */
@Component
public class LinkedGroupAssignmentTracker {
  /** Activates an application at its event version or the known group's assignment version. */
  public AssignmentChange applicationReady(
      GroupAssignmentTrackingStore store, UUID applicationId, long assignmentVersion) {
    TrackedApplication application = application(store, applicationId);
    if (application.active()) {
      return change(application, application.caseworkerId());
    }
    long currentVersion =
        application.groupId() == null ? assignmentVersion : application.groupAssignmentVersion();
    TrackedApplication updated =
        application.withActive(true).withAssignment(application.caseworkerId(), currentVersion);
    store.saveApplication(updated);
    return change(updated, application.caseworkerId());
  }

  /** Marks a tracked application inactive while preserving its shown assignment. */
  public void applicationDecided(GroupAssignmentTrackingStore store, UUID applicationId) {
    store
        .findApplication(applicationId)
        .ifPresent(application -> store.saveApplication(application.withActive(false)));
  }

  /** Records an application's own assignment fact in the projection's tracking store. */
  public AssignmentChange directAssignmentChanged(
      GroupAssignmentTrackingStore store,
      UUID applicationId,
      @Nullable UUID caseworkerId,
      long assignmentVersion) {
    TrackedApplication application = application(store, applicationId);
    TrackedApplication updated = application.withAssignment(caseworkerId, assignmentVersion);
    store.saveApplication(updated);
    return change(updated, application.caseworkerId());
  }

  /** Tracks a newly created group and assigns each member to that new owner. */
  public List<AssignmentChange> groupCreated(
      GroupAssignmentTrackingStore store, UUID groupId, List<UUID> memberApplicationIds) {
    if (anyMember(store, groupId).isPresent()) {
      return List.of();
    }
    List<AssignmentChange> changes = new ArrayList<>();
    for (UUID memberApplicationId : memberApplicationIds) {
      TrackedApplication application = application(store, memberApplicationId);
      TrackedApplication updated = application.joinGroup(groupId, null, 0L);
      if (application.active()) {
        updated = updated.withAssignment(application.caseworkerId(), 0L);
        changes.add(change(updated, application.caseworkerId()));
      }
      store.saveApplication(updated);
    }
    return changes;
  }

  /** Tracks a new member and returns its updated assignment when it is active. */
  public List<AssignmentChange> memberAdded(
      GroupAssignmentTrackingStore store, UUID groupId, UUID memberApplicationId) {
    TrackedApplication application = application(store, memberApplicationId);
    if (groupId.equals(application.groupId())) {
      return List.of();
    }
    Optional<TrackedApplication> existingMember = anyMember(store, groupId);
    @Nullable UUID groupCaseworkerId =
        existingMember.map(TrackedApplication::groupCaseworkerId).orElse(null);
    long groupAssignmentVersion =
        existingMember.map(TrackedApplication::groupAssignmentVersion).orElse(0L);
    TrackedApplication joined =
        application.joinGroup(groupId, groupCaseworkerId, groupAssignmentVersion);
    if (application.active()) {
      joined =
          joined.withAssignment(
              groupCaseworkerId != null ? groupCaseworkerId : application.caseworkerId(),
              groupAssignmentVersion);
    }
    store.saveApplication(joined);
    return application.active() ? List.of(change(joined, application.caseworkerId())) : List.of();
  }

  /** Removes group ownership from the specified members. */
  public void membersLeft(
      GroupAssignmentTrackingStore store, UUID groupId, List<UUID> leavingApplicationIds) {
    for (UUID applicationId : leavingApplicationIds) {
      store
          .findApplication(applicationId)
          .filter(application -> groupId.equals(application.groupId()))
          .ifPresent(application -> store.saveApplication(application.leaveGroup()));
    }
  }

  /** Applies a newer group assignment to all active members tracked in that group. */
  public List<AssignmentChange> groupAssignmentChanged(
      GroupAssignmentTrackingStore store,
      UUID groupId,
      @Nullable UUID caseworkerId,
      long assignmentVersion) {
    List<TrackedApplication> members = store.findApplicationsInGroup(groupId);
    if (members.isEmpty()) {
      return List.of();
    }
    if (members.getFirst().groupAssignmentVersion() >= assignmentVersion) {
      return List.of();
    }
    List<AssignmentChange> changes = new ArrayList<>();
    for (TrackedApplication member : members) {
      TrackedApplication updated = member.withGroupAssignment(caseworkerId, assignmentVersion);
      if (member.active()) {
        updated = updated.withAssignment(caseworkerId, assignmentVersion);
        changes.add(change(updated, member.caseworkerId()));
      }
      store.saveApplication(updated);
    }
    return changes;
  }

  /** Applies an inherited group assignment only when the member is active in that group. */
  public Optional<AssignmentChange> memberInherited(
      GroupAssignmentTrackingStore store,
      UUID groupId,
      UUID memberApplicationId,
      UUID caseworkerId,
      long assignmentVersion) {
    TrackedApplication application = application(store, memberApplicationId);
    if (!application.active() || !groupId.equals(application.groupId())) {
      return Optional.empty();
    }
    TrackedApplication updated = application.withAssignment(caseworkerId, assignmentVersion);
    store.saveApplication(updated);
    return Optional.of(change(updated, application.caseworkerId()));
  }

  /** Returns an active member to direct ownership, preserving assignments for inactive members. */
  public Optional<AssignmentChange> assignmentReclaimed(
      GroupAssignmentTrackingStore store,
      UUID applicationId,
      @Nullable UUID caseworkerId,
      long assignmentVersion) {
    Optional<TrackedApplication> found = store.findApplication(applicationId);
    if (found.isEmpty()) {
      return Optional.empty();
    }
    TrackedApplication application = found.get();
    if (!application.active()) {
      store.saveApplication(application.leaveGroup());
      return Optional.empty();
    }
    TrackedApplication updated =
        application.leaveGroup().withAssignment(caseworkerId, assignmentVersion);
    store.saveApplication(updated);
    return Optional.of(change(updated, application.caseworkerId()));
  }

  private TrackedApplication application(GroupAssignmentTrackingStore store, UUID applicationId) {
    return store.findApplication(applicationId).orElse(TrackedApplication.untracked(applicationId));
  }

  private Optional<TrackedApplication> anyMember(GroupAssignmentTrackingStore store, UUID groupId) {
    return store.findApplicationsInGroup(groupId).stream().findFirst();
  }

  private AssignmentChange change(
      TrackedApplication application, @Nullable UUID previousCaseworkerId) {
    return new AssignmentChange(
        application.applicationId(),
        application.groupId(),
        previousCaseworkerId,
        application.caseworkerId(),
        application.assignmentVersion());
  }
}
