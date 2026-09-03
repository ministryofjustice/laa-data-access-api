package uk.gov.justice.laa.dstew.access.command.worklist.route;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;

/** Durable write-side lookup used exclusively to select command authority. */
@Entity
@Table(name = "work_item_route")
@Getter
@NoArgsConstructor
public class WorkItemRoute {
  @Id
  @Column(name = "work_item_id", nullable = false)
  private UUID workItemId;

  @Enumerated(EnumType.STRING)
  @Column(name = "work_item_type", nullable = false)
  private WorkItemType workItemType;

  @Enumerated(EnumType.STRING)
  @Column(name = "route_kind", nullable = false)
  private WorkItemRouteKind routeKind;

  @Column(name = "aggregate_id", nullable = false)
  private UUID aggregateId;

  @Column(name = "group_id")
  private UUID groupId;

  @Column(name = "membership_version", nullable = false)
  private long membershipVersion;

  @Version
  @Column(name = "route_version", nullable = false)
  private long routeVersion;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** Creates the initial durable route for a newly created work item. */
  public WorkItemRoute(
      WorkItemType type,
      UUID workItemId,
      WorkItemRouteKind routeKind,
      UUID aggregateId,
      UUID groupId,
      long membershipVersion,
      Instant occurredAt) {
    this.workItemId = workItemId;
    this.workItemType = type;
    this.routeKind = routeKind;
    this.aggregateId = aggregateId;
    this.groupId = groupId;
    this.membershipVersion = membershipVersion;
    this.createdAt = occurredAt;
    this.updatedAt = occurredAt;
  }
}
