package uk.gov.justice.laa.dstew.access.query.groupassignment;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Shared fields for a projection-owned tracked application row. */
@MappedSuperclass
@Getter
@NoArgsConstructor
public abstract class GroupAssignmentApplicationRow {
  @Id
  @Column(name = "application_id", nullable = false)
  private UUID applicationId;

  @Column(name = "group_id")
  private UUID groupId;

  @Column(name = "active", nullable = false)
  private boolean active;

  @Column(name = "caseworker_id")
  private UUID caseworkerId;

  @Column(name = "assignment_version", nullable = false)
  private long assignmentVersion;

  /** Copies the tracked application values into this persistence row. */
  public void copyFrom(TrackedApplication application) {
    applicationId = application.applicationId();
    groupId = application.groupId();
    active = application.active();
    caseworkerId = application.caseworkerId();
    assignmentVersion = application.assignmentVersion();
  }

  /** Converts this persistence row to its immutable tracker value. */
  public TrackedApplication toTrackedApplication() {
    return new TrackedApplication(applicationId, groupId, active, caseworkerId, assignmentVersion);
  }
}
