package uk.gov.justice.laa.dstew.access.command.worklist.route;

import org.axonframework.messaging.core.annotation.Namespace;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreatedEvent;
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

  /** Establishes direct command authority for an application. */
  @EventHandler
  @Transactional
  public void on(ApplicationCreatedEvent event) {
    routes.save(
        new WorkItemRoute(
            WorkItemType.APPLICATION,
            event.applicationId(),
            WorkItemRouteKind.STANDALONE,
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
}
