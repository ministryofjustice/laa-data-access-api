package uk.gov.justice.laa.dstew.access.command.application.update;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;

/** Thin, replayable fact that an Application's immutable data and current state advanced. */
@Event
public record ApplicationUpdatedEvent(
    @EventTag(key = "ApplicationAggregate") UUID applicationId,
    long applicationVersion,
    long applicationDataVersion,
    String previousStatus,
    String status,
    String applicationType,
    UUID applyApplicationId,
    Instant occurredAt) {

  /** Whether this update represents a transition into submitted state. */
  public boolean enteredSubmitted() {
    return !"APPLICATION_SUBMITTED".equals(previousStatus)
        && "APPLICATION_SUBMITTED".equals(status);
  }
}
