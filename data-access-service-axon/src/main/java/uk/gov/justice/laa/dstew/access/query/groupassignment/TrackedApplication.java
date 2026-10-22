package uk.gov.justice.laa.dstew.access.query.groupassignment;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** One projection's last observed owner, activity and shown assignment for an application. */
public record TrackedApplication(
    UUID applicationId,
    @Nullable UUID groupId,
    boolean active,
    @Nullable UUID caseworkerId,
    long assignmentVersion) {
  public static TrackedApplication untracked(UUID applicationId) {
    return new TrackedApplication(applicationId, null, false, null, 0L);
  }

  public TrackedApplication withGroupId(@Nullable UUID groupId) {
    return new TrackedApplication(applicationId, groupId, active, caseworkerId, assignmentVersion);
  }

  public TrackedApplication withActive(boolean active) {
    return new TrackedApplication(applicationId, groupId, active, caseworkerId, assignmentVersion);
  }

  public TrackedApplication withAssignment(@Nullable UUID caseworkerId, long assignmentVersion) {
    return new TrackedApplication(applicationId, groupId, active, caseworkerId, assignmentVersion);
  }
}
