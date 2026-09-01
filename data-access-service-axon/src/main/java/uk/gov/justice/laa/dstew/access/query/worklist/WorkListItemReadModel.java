package uk.gov.justice.laa.dstew.access.query.worklist;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;

/** Disposable tracking row representing exactly one active work item. */
@Entity
@Table(name = "work_list_item")
@Getter
@Setter
@NoArgsConstructor
public class WorkListItemReadModel {
  @EmbeddedId private WorkListItemId id;

  @Column(name = "application_id", nullable = false)
  private UUID applicationId;

  @Column(name = "parent_application_id")
  private UUID parentApplicationId;

  @Column(name = "laa_reference")
  private String laaReference;

  @Column(name = "used_delegated_functions")
  private Boolean usedDelegatedFunctions;

  @Column(name = "category_of_law")
  private String categoryOfLaw;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "matter_types")
  private List<String> matterTypes;

  @Column(name = "application_status")
  private String applicationStatus;

  @Column(name = "submitted_at")
  private Instant submittedAt;

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
      UUID applicationId,
      UUID parentApplicationId,
      Instant updatedAt,
      long itemVersion,
      long projectionPosition) {
    this.id = new WorkListItemId(itemType, itemId);
    this.applicationId = applicationId;
    this.parentApplicationId = parentApplicationId;
    this.submittedAt = updatedAt;
    this.updatedAt = updatedAt;
    this.assignmentBoundaryType = "DIRECT";
    this.assignmentBoundaryId = itemId;
    this.assignmentVersion = 0L;
    this.itemVersion = itemVersion;
    this.projectionPosition = projectionPosition;
  }
}


