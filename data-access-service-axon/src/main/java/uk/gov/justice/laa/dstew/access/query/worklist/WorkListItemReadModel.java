package uk.gov.justice.laa.dstew.access.query.worklist;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uk.gov.justice.laa.dstew.access.command.application.AutoGrantedState;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;

/** Disposable tracking row representing exactly one active work item. */
@Entity
@Table(name = "work_list_item")
@Getter
@Setter
@NoArgsConstructor
public class WorkListItemReadModel {
  @Id
  @Column(name = "item_id", nullable = false)
  private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(name = "item_type", nullable = false)
  private WorkItemType itemType;

  @Column(name = "parent_application_id")
  private UUID parentApplicationId;

  @Column(name = "application_status")
  private String applicationStatus;

  @Column(name = "prior_authority_type")
  private String priorAuthorityType;

  @Column(name = "expert_type")
  private String expertType;

  @Column(name = "submitted_at")
  private Instant submittedAt;

  @Column(name = "ready_at")
  private Instant readyAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "autogranted")
  private AutoGrantedState autoGranted;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "assignee_id")
  private UUID assigneeId;

  @Column(name = "assignment_boundary_type", nullable = false)
  private String assignmentBoundaryType;

  @Column(name = "assignment_boundary_id", nullable = false)
  private UUID assignmentBoundaryId;

  @Column(name = "group_id")
  private UUID groupId;

  @Column(name = "assignment_version", nullable = false)
  private long assignmentVersion;

  @Column(name = "item_version", nullable = false)
  private long itemVersion;

  @Column(name = "projection_position", nullable = false)
  private long projectionPosition;

  /** Creates an unassigned direct work-list row. */
  public WorkListItemReadModel(
      WorkItemType itemType,
      UUID itemId,
      UUID parentApplicationId,
      Instant initialUpdatedAt,
      long itemVersion,
      long projectionPosition) {
    this.id = itemId;
    this.itemType = itemType;
    this.parentApplicationId = parentApplicationId;
    this.submittedAt = initialUpdatedAt;
    this.updatedAt = initialUpdatedAt;
    useDirectBoundary();
    this.assignmentVersion = 0L;
    this.itemVersion = itemVersion;
    this.projectionPosition = projectionPosition;
  }

  /** Returns the application whose summary describes this item: itself, or its parent. */
  public UUID subjectApplicationId() {
    return parentApplicationId == null ? id : parentApplicationId;
  }

  /** Makes the linked group the boundary that assignment changes apply to. */
  public void useLinkedGroupBoundary(UUID linkedGroupId) {
    this.assignmentBoundaryType = "LINKED_GROUP";
    this.assignmentBoundaryId = linkedGroupId;
    this.groupId = linkedGroupId;
  }

  /** Makes this item its own assignment boundary. */
  public void useDirectBoundary() {
    this.assignmentBoundaryType = "DIRECT";
    this.assignmentBoundaryId = id;
    this.groupId = null;
  }
}
