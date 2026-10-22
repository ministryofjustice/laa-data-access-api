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
    Optional<TrackedGroup> group =
        application.groupId() == null ? Optional.empty() : store.findGroup(application.groupId());
    TrackedApplication updated =
        application
            .withActive(true)
            .withAssignment(
                application.caseworkerId(),
                group.map(TrackedGroup::assignmentVersion).orElse(assignmentVersion));
    if (group.isEmpty()) {
      updated = updated.withGroupId(null);
    }
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
    if (store.findGroup(groupId).isPresent()) {
      return List.of();
    }
    store.saveGroup(new TrackedGroup(groupId, null, 0L));
    List<AssignmentChange> changes = new ArrayList<>();
    for (UUID memberApplicationId : memberApplicationIds) {
      TrackedApplication application = application(store, memberApplicationId);
      TrackedApplication updated = application.withGroupId(groupId);
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
    TrackedGroup group = store.findGroup(groupId).orElse(new TrackedGroup(groupId, null, 0L));
    store.saveGroup(group);
    TrackedApplication joined = application.withGroupId(groupId);
    if (application.active()) {
      joined =
          joined.withAssignment(
              group.caseworkerId() != null ? group.caseworkerId() : application.caseworkerId(),
              group.assignmentVersion());
    }
    store.saveApplication(joined);
    return application.active() ? List.of(change(joined, application.caseworkerId())) : List.of();
  }

  /** Removes group ownership from the specified members and optionally deletes the group. */
  public void membersLeft(
      GroupAssignmentTrackingStore store,
      UUID groupId,
      List<UUID> leavingApplicationIds,
      boolean dissolved) {
    for (UUID applicationId : leavingApplicationIds) {
      store
          .findApplication(applicationId)
          .filter(application -> groupId.equals(application.groupId()))
          .ifPresent(application -> store.saveApplication(application.withGroupId(null)));
    }
    if (dissolved) {
      store.deleteGroup(groupId);
    }
  }

  /** Applies a newer group assignment to all active members tracked in that group. */
  public List<AssignmentChange> groupAssignmentChanged(
      GroupAssignmentTrackingStore store,
      UUID groupId,
      @Nullable UUID caseworkerId,
      long assignmentVersion) {
    Optional<TrackedGroup> existing = store.findGroup(groupId);
    if (existing.isEmpty()) {
      store.saveGroup(new TrackedGroup(groupId, caseworkerId, assignmentVersion));
      return List.of();
    }
    TrackedGroup group = existing.get();
    if (group.assignmentVersion() >= assignmentVersion) {
      return List.of();
    }
    store.saveGroup(group.withAssignment(caseworkerId, assignmentVersion));
    List<AssignmentChange> changes = new ArrayList<>();
    for (TrackedApplication member : store.findApplicationsInGroup(groupId)) {
      if (!member.active()) {
        continue;
      }
      TrackedApplication updated = member.withAssignment(caseworkerId, assignmentVersion);
      store.saveApplication(updated);
      changes.add(change(updated, member.caseworkerId()));
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
      store.saveApplication(application.withGroupId(null));
      return Optional.empty();
    }
    TrackedApplication updated =
        application.withGroupId(null).withAssignment(caseworkerId, assignmentVersion);
    store.saveApplication(updated);
    return Optional.of(change(updated, application.caseworkerId()));
  }

  private TrackedApplication application(GroupAssignmentTrackingStore store, UUID applicationId) {
    return store.findApplication(applicationId).orElse(TrackedApplication.untracked(applicationId));
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
