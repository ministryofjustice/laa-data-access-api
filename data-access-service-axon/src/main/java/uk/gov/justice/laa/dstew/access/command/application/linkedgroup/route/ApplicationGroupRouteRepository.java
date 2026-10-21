package uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/** Repository for locking and retrieving durable application membership routes. */
public interface ApplicationGroupRouteRepository
    extends JpaRepository<ApplicationGroupRoute, UUID> {

  /** Locks the supplied application routes in ascending identifier order before classification. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select route
      from ApplicationGroupRoute route
      where route.applicationId in :applicationIds
      order by route.applicationId
      """)
  List<ApplicationGroupRoute> findAllByApplicationIdInForUpdate(Collection<UUID> applicationIds);

  /**
   * Locks both link endpoints and every route in the target's current group in identifier order.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select route
      from ApplicationGroupRoute route
      where route.applicationId in :applicationIds
         or route.groupId = (
             select targetRoute.groupId
             from ApplicationGroupRoute targetRoute
             where targetRoute.applicationId = :targetApplicationId)
      order by route.applicationId
      """)
  List<ApplicationGroupRoute> findLinkRoutesForUpdate(
      Collection<UUID> applicationIds, UUID targetApplicationId);

  /** Locks every route in the supplied group in ascending application identifier order. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select route
      from ApplicationGroupRoute route
      where route.groupId = :groupId
      order by route.applicationId
      """)
  List<ApplicationGroupRoute> findAllByGroupIdForUpdate(UUID groupId);

  /** Locks a single application route for exclusive membership updates. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select route
      from ApplicationGroupRoute route
      where route.applicationId = :applicationId
      """)
  Optional<ApplicationGroupRoute> findByApplicationIdForUpdate(UUID applicationId);

  /** Reads membership without attaching the route entity to the persistence context. */
  Optional<ApplicationGroupRouteMembership> findMembershipByApplicationId(UUID applicationId);
}
