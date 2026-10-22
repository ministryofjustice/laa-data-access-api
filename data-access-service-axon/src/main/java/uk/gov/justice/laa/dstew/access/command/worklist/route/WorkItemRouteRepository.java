package uk.gov.justice.laa.dstew.access.command.worklist.route;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/** Repository for command-side route discovery only. */
public interface WorkItemRouteRepository extends JpaRepository<WorkItemRoute, UUID> {
  Optional<WorkItemRoute> findByWorkItemId(UUID workItemId);

  /** Reads route membership without attaching a route entity to the persistence context. */
  Optional<WorkItemRouteMembership> findMembershipByWorkItemId(UUID workItemId);

  /** Locks one work-item route for an assignment decision. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select route
      from WorkItemRoute route
      where route.workItemId = :workItemId
      """)
  Optional<WorkItemRoute> findByWorkItemIdForUpdate(UUID workItemId);

  /** Locks the supplied work-item routes in ascending identifier order. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select route
      from WorkItemRoute route
      where route.workItemId in :workItemIds
      order by route.workItemId
      """)
  List<WorkItemRoute> findAllByWorkItemIdInForUpdate(Collection<UUID> workItemIds);

  /** Locks every work-item route in a linked group in ascending work-item identifier order. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select route
      from WorkItemRoute route
      where route.groupId = :groupId
      order by route.workItemId
      """)
  List<WorkItemRoute> findAllByGroupIdForUpdate(UUID groupId);
}
