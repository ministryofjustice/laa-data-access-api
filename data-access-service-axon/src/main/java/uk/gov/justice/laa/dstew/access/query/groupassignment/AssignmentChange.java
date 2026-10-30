package uk.gov.justice.laa.dstew.access.query.groupassignment;

import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * The assignment a projection must now show for one application; {@code groupId} is the owning
 * linked group, or null when the application owns its assignment.
 */
public record AssignmentChange(
    UUID applicationId,
    @Nullable UUID groupId,
    @Nullable UUID previousCaseworkerId,
    @Nullable UUID caseworkerId,
    long assignmentVersion) {
  public boolean caseworkerChanged() {
    return !Objects.equals(previousCaseworkerId, caseworkerId);
  }
}
