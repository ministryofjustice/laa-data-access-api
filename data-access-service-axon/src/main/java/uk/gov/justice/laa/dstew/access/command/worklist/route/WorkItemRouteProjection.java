package uk.gov.justice.laa.dstew.access.command.worklist.route;

import org.axonframework.messaging.core.annotation.Namespace;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.ready.ApplicationReadyForManualAssessmentEvent;
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

  /** Establishes direct command authority when an application requires manual assessment. */
  @EventHandler
  @Transactional
  public void on(ApplicationReadyForManualAssessmentEvent event) {
    routes.save(
        new WorkItemRoute(
            WorkItemType.APPLICATION,
            event.applicationId(),
            WorkItemRouteKind.STANDALONE,
            null,
            0L,
            event.occurredAt()));
  }

  /**
   * Removes application command authority after a terminal decision; absent routes are harmless.
   */
  @EventHandler
  @Transactional
  public void on(ApplicationDecisionMadeEvent event) {
    routes.deleteById(event.applicationId());
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
            null,
            0L,
            event.occurredAt()));
  }
}
