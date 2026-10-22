package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Result of removing a member: the applications leaving the group, in ascending ID order, and the
 * group's assignment at removal.
 */
public record LinkedGroupRemovalResult(
    UUID groupId,
    List<UUID> leavingApplicationIds,
    @Nullable UUID caseworkerId,
    long assignmentVersion) {

  /** Validates and defensively copies the applications leaving the group. */
  public LinkedGroupRemovalResult {
    Objects.requireNonNull(groupId, "groupId must not be null");
    Objects.requireNonNull(leavingApplicationIds, "leavingApplicationIds must not be null");
    leavingApplicationIds = List.copyOf(leavingApplicationIds);
  }
}
