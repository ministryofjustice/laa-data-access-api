package uk.gov.justice.laa.dstew.access.query.groupassignment;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** One projection's last observed assignment for a linked group. */
public record TrackedGroup(UUID groupId, @Nullable UUID caseworkerId, long assignmentVersion) {
  public TrackedGroup withAssignment(@Nullable UUID caseworkerId, long assignmentVersion) {
    return new TrackedGroup(groupId, caseworkerId, assignmentVersion);
  }
}
