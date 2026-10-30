package uk.gov.justice.laa.dstew.access.command.worklist.route;

import lombok.RequiredArgsConstructor;
import org.axonframework.messaging.core.annotation.Namespace;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupDissolvedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.MemberAddedToGroupEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.MemberRemovedFromGroupEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationGroupRoute;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationGroupRouteKind;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationGroupRouteRepository;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthoritySubmittedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision.PriorAuthorityDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.ready.ApplicationReadyForManualAssessmentEvent;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;

/**
 * Synchronously maintains command routing state; it is deliberately not a replayable read model.
 */
@Component
@Namespace("work-item-route")
@RequiredArgsConstructor
public class WorkItemRouteProjection {
  private final WorkItemRouteRepository routes;
  private final ApplicationGroupRouteRepository applicationGroupRoutes;

  /** Establishes direct command authority when an application requires manual assessment. */
  @EventHandler
  @Transactional
  public void on(ApplicationReadyForManualAssessmentEvent event) {
    var applicationRoute =
        applicationGroupRoutes.findByApplicationIdForUpdate(event.applicationId());
    if (routes.findByWorkItemIdForUpdate(event.applicationId()).isPresent()) {
      return;
    }
    var routeKind =
        applicationRoute
                    .map(ApplicationGroupRoute::getRouteKind)
                    .orElse(ApplicationGroupRouteKind.STANDALONE)
                == ApplicationGroupRouteKind.LINKED_GROUP
            ? WorkItemRouteKind.LINKED_GROUP
            : WorkItemRouteKind.STANDALONE;
    var groupId =
        routeKind == WorkItemRouteKind.LINKED_GROUP
            ? applicationRoute.map(ApplicationGroupRoute::getGroupId).orElse(null)
            : null;
    routes.save(
        new WorkItemRoute(
            WorkItemType.APPLICATION,
            event.applicationId(),
            routeKind,
            groupId,
            0L,
            event.occurredAt()));
  }

  /** Moves existing application work-item routes into a newly created group. */
  @EventHandler
  @Transactional
  public void on(LinkedApplicationGroupCreatedEvent event) {
    var memberIds = event.memberApplicationIds().stream().sorted().toList();
    var memberRoutes = routes.findAllByWorkItemIdInForUpdate(memberIds);
    memberRoutes.forEach(route -> route.joinGroup(event.groupId(), event.occurredAt()));
    routes.saveAll(memberRoutes);
  }

  /** Moves a newly linked member's existing work-item route into its group. */
  @EventHandler
  @Transactional
  public void on(MemberAddedToGroupEvent event) {
    routes
        .findByWorkItemIdForUpdate(event.memberId())
        .ifPresent(
            route -> {
              route.joinGroup(event.groupId(), event.occurredAt());
              routes.save(route);
            });
  }

  /** Restores a removed member's existing work-item route to standalone ownership. */
  @EventHandler
  @Transactional
  public void on(MemberRemovedFromGroupEvent event) {
    routes
        .findByWorkItemIdForUpdate(event.memberId())
        .ifPresent(
            route -> {
              route.leaveGroup(event.groupId(), event.occurredAt());
              routes.save(route);
            });
  }

  /** Restores every remaining work-item route to standalone ownership on group dissolution. */
  @EventHandler
  @Transactional
  public void on(LinkedApplicationGroupDissolvedEvent event) {
    var memberIds = event.memberApplicationIds().stream().sorted().toList();
    var memberRoutes = routes.findAllByWorkItemIdInForUpdate(memberIds);
    memberRoutes.forEach(route -> route.leaveGroup(event.groupId(), event.occurredAt()));
    routes.saveAll(memberRoutes);
  }

  /**
   * Removes application command authority after a terminal decision; absent routes are harmless.
   */
  @EventHandler
  @Transactional
  public void on(ApplicationDecisionMadeEvent event) {
    routes.deleteById(event.applicationId());
  }

  /** Establishes direct prior-authority command ownership at submission. */
  @EventHandler
  @Transactional
  public void on(PriorAuthoritySubmittedEvent event) {
    routes.save(
        new WorkItemRoute(
            WorkItemType.PRIOR_AUTHORITY,
            event.priorAuthorityId(),
            WorkItemRouteKind.STANDALONE,
            null,
            0L,
            event.occurredAt()));
  }

  /** Removes prior-authority command authority after a terminal decision. */
  @EventHandler
  @Transactional
  public void on(PriorAuthorityDecisionMadeEvent event) {
    routes.deleteById(event.priorAuthorityId());
  }
}
