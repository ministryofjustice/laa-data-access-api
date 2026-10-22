package uk.gov.justice.laa.dstew.access.query.groupassignment;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Shared fields for a projection-owned tracked group row. */
@MappedSuperclass
@Getter
@NoArgsConstructor
public abstract class GroupAssignmentGroupRow {
  @Id
  @Column(name = "group_id", nullable = false)
  private UUID groupId;

  @Column(name = "caseworker_id")
  private UUID caseworkerId;

  @Column(name = "assignment_version", nullable = false)
  private long assignmentVersion;

  /** Copies the tracked group values into this persistence row. */
  public void copyFrom(TrackedGroup group) {
    groupId = group.groupId();
    caseworkerId = group.caseworkerId();
    assignmentVersion = group.assignmentVersion();
  }

  /** Converts this persistence row to its immutable tracker value. */
  public TrackedGroup toTrackedGroup() {
    return new TrackedGroup(groupId, caseworkerId, assignmentVersion);
  }
}
