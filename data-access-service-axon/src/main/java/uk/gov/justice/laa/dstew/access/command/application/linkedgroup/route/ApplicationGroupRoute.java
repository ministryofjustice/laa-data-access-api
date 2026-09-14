package uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * Durable write-side route used to determine an application's linked-group membership authority.
 */
@Entity
@Table(name = "application_group_route")
@Getter
@NoArgsConstructor
public class ApplicationGroupRoute {
  @Id
  @Column(name = "application_id", nullable = false)
  private UUID applicationId;

  @Enumerated(EnumType.STRING)
  @Column(name = "route_kind", nullable = false)
  private ApplicationGroupRouteKind routeKind;

  @Column(name = "group_id")
  private @Nullable UUID groupId;

  @Version
  @Column(name = "route_version", nullable = false)
  private long routeVersion;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** Creates the initial membership route for an application. */
  public ApplicationGroupRoute(
      UUID applicationId,
      ApplicationGroupRouteKind routeKind,
      @Nullable UUID groupId,
      Instant occurredAt) {
    this.applicationId = applicationId;
    this.routeKind = routeKind;
    this.groupId = groupId;
    this.createdAt = occurredAt;
    this.updatedAt = occurredAt;
  }

  /** Moves a standalone route into a linked group, or no-ops if already in the same group. */
  void join(UUID newGroupId, Instant occurredAt) {
    if (routeKind == ApplicationGroupRouteKind.LINKED_GROUP
        && !Objects.equals(groupId, newGroupId)) {
      throw new ApplicationLinkConflictException(
          "Application " + applicationId + " already belongs to a different linked group");
    }
    if (routeKind == ApplicationGroupRouteKind.LINKED_GROUP) {
      return;
    }
    routeKind = ApplicationGroupRouteKind.LINKED_GROUP;
    groupId = newGroupId;
    updatedAt = occurredAt;
  }
}
