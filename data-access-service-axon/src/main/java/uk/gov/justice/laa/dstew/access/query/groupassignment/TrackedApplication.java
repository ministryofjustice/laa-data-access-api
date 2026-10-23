package uk.gov.justice.laa.dstew.access.query.groupassignment;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** One projection's last observed owner, activity and shown assignment for an application. */
public record TrackedApplication(
    UUID applicationId,
    @Nullable UUID groupId,
    boolean active,
    @Nullable UUID caseworkerId,
    long assignmentVersion,
    @Nullable UUID groupCaseworkerId,
    long groupAssignmentVersion) {
  /** Returns an inactive application with no observed assignment or group. */
  public static TrackedApplication untracked(UUID applicationId) {
    return new TrackedApplication(applicationId, null, false, null, 0L, null, 0L);
  }

  /** Joins this application to a group with the projection's current group assignment. */
  public TrackedApplication joinGroup(
      UUID groupId, @Nullable UUID groupCaseworkerId, long groupAssignmentVersion) {
    return new TrackedApplication(
        applicationId,
        groupId,
        active,
        caseworkerId,
        assignmentVersion,
        groupCaseworkerId,
        groupAssignmentVersion);
  }

  /** Removes this application from its group and clears the observed group assignment. */
  public TrackedApplication leaveGroup() {
    return new TrackedApplication(
        applicationId, null, active, caseworkerId, assignmentVersion, null, 0L);
  }

  /** Updates the group's assignment without changing this application's shown assignment. */
  public TrackedApplication withGroupAssignment(
      @Nullable UUID groupCaseworkerId, long groupAssignmentVersion) {
    return new TrackedApplication(
        applicationId,
        groupId,
        active,
        caseworkerId,
        assignmentVersion,
        groupCaseworkerId,
        groupAssignmentVersion);
  }

  /** Returns this application with the supplied active-work-item state. */
  public TrackedApplication withActive(boolean active) {
    return new TrackedApplication(
        applicationId,
        groupId,
        active,
        caseworkerId,
        assignmentVersion,
        groupCaseworkerId,
        groupAssignmentVersion);
  }

  /** Returns this application with the supplied displayed assignment. */
  public TrackedApplication withAssignment(@Nullable UUID caseworkerId, long assignmentVersion) {
    return new TrackedApplication(
        applicationId,
        groupId,
        active,
        caseworkerId,
        assignmentVersion,
        groupCaseworkerId,
        groupAssignmentVersion);
  }
}
