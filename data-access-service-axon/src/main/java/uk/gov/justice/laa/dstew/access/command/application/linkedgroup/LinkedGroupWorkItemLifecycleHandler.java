package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.core.annotation.Namespace;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.ready.ApplicationReadyForManualAssessmentEvent;
import uk.gov.justice.laa.dstew.access.command.worklist.route.WorkItemRoute;
import uk.gov.justice.laa.dstew.access.command.worklist.route.WorkItemRouteKind;
import uk.gov.justice.laa.dstew.access.command.worklist.route.WorkItemRouteRepository;

/** Converts application lifecycle events into group-owned member eligibility changes. */
@Component
@Namespace("linked-group-work-item-lifecycle")
public class LinkedGroupWorkItemLifecycleHandler {
  private final WorkItemRouteRepository routes;
  private final CommandGateway commandGateway;

  public LinkedGroupWorkItemLifecycleHandler(
      WorkItemRouteRepository routes, CommandGateway commandGateway) {
    this.routes = routes;
    this.commandGateway = commandGateway;
  }

  /** Activates an eligible application only after its durable route resolves to a linked group. */
  @EventHandler
  public void on(ApplicationReadyForManualAssessmentEvent event) {
    linkedRoute(event.applicationId())
        .ifPresent(
            route ->
                commandGateway.sendAndWait(
                    new ActivateLinkedGroupMemberWorkItemCommand(
                        route.getGroupId(),
                        event.applicationId(),
                        route.getMembershipVersion(),
                        event.occurredAt())));
  }

  /** Removes a terminally decided application from its group-owned active work set. */
  @EventHandler
  public void on(ApplicationDecisionMadeEvent event) {
    linkedRoute(event.applicationId())
        .ifPresent(
            route ->
                commandGateway.sendAndWait(
                    new DeactivateLinkedGroupMemberWorkItemCommand(
                        route.getGroupId(),
                        event.applicationId(),
                        route.getMembershipVersion(),
                        event.occurredAt())));
  }

  private java.util.Optional<WorkItemRoute> linkedRoute(java.util.UUID applicationId) {
    return routes
        .findByWorkItemId(applicationId)
        .filter(route -> route.getRouteKind() == WorkItemRouteKind.LINKED_GROUP);
  }
}

