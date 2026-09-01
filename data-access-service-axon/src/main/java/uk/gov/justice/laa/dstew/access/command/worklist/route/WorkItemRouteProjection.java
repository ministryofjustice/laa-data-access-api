package uk.gov.justice.laa.dstew.access.command.worklist.route;

import java.util.UUID;
import org.axonframework.messaging.core.annotation.Namespace;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.MemberAddedToGroupEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;

/**
 * Synchronously maintains command routing state; it is deliberately not a replayable read model.
 */
@Component
@Namespace("work-item-route")
public class WorkItemRouteProjection {
  private final WorkItemRouteRepository routes;

  public WorkItemRouteProjection(WorkItemRouteRepository routes) {
    this.routes = routes;
  }

  /** Establishes direct authority, blocking it while linked-group initialisation is pending. */
  @EventHandler
  @Transactional
  public void on(ApplicationCreatedEvent event) {
    WorkItemRouteKind kind =
        event.leadApplicationId() == null
            ? WorkItemRouteKind.STANDALONE
            : WorkItemRouteKind.PENDING_LINKED_GROUP;
    routes.save(
        new WorkItemRoute(
            WorkItemType.APPLICATION,
            event.applicationId(),
            kind,
            event.applicationId(),
            null,
            0L,
            event.occurredAt()));
  }

  /** Establishes direct prior-authority command ownership at creation. */
  @EventHandler
  @Transactional
  public void on(PriorAuthorityCreatedEvent event) {
    routes.save(
        new WorkItemRoute(
            WorkItemType.PRIOR_AUTHORITY,
            event.submissionId(),
            WorkItemRouteKind.STANDALONE,
            event.submissionId(),
            null,
            0L,
            event.occurredAt()));
  }

  /** Makes every immutable initial member route to the successfully created group. */
  @EventHandler
  @Transactional
  public void on(LinkedApplicationGroupCreatedEvent event) {
    event
        .memberApplicationIds()
        .forEach(id -> moveToGroup(id, event.groupId(), 1L, event.occurredAt()));
  }

  /** Makes a subsequently added member route to the established group. */
  @EventHandler
  @Transactional
  public void on(MemberAddedToGroupEvent event) {
    long nextMembershipVersion =
        routes.findAllByGroupId(event.groupId()).stream()
                .mapToLong(WorkItemRoute::getMembershipVersion)
                .max()
                .orElse(1L)
            + 1L;
    routes
        .findAllByGroupId(event.groupId())
        .forEach(
            route ->
                route.moveTo(
                    WorkItemRouteKind.LINKED_GROUP,
                    event.groupId(),
                    event.groupId(),
                    nextMembershipVersion,
                    event.occurredAt()));
    moveToGroup(event.memberId(), event.groupId(), nextMembershipVersion, event.occurredAt());
  }

  private void moveToGroup(
      UUID applicationId, UUID groupId, long membershipVersion, java.time.Instant occurredAt) {
    WorkItemRoute route =
        routes
            .findByWorkItemId(applicationId)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Cannot route application without a direct route: " + applicationId));
    route.moveTo(WorkItemRouteKind.LINKED_GROUP, groupId, groupId, membershipVersion, occurredAt);
    routes.save(route);
  }
}
