package uk.gov.justice.laa.dstew.access.command.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;

/** Thin event establishing the non-sensitive initial state of an Application aggregate. */
@Event
public record ApplicationCreatedEvent(
    @EventTag(key = "ApplicationAggregate") UUID applicationId,
    long applicationDataVersion,
    String requestFingerprint,
    String status,
    int schemaVersion,
    String applicationType,
    UUID applyApplicationId,
    Instant occurredAt,
    UUID leadApplicationId,
    List<UUID> associatedApplicationIds) {

  public ApplicationCreatedEvent {
    associatedApplicationIds = List.copyOf(associatedApplicationIds);
  }
}
