package uk.gov.justice.laa.dstew.access.command.application.decision;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;
import uk.gov.justice.laa.dstew.access.command.application.AutoGrantedState;

/** Thin event recording that an Application decision was stored in an immutable data version. */
@Event
public record ApplicationDecisionMadeEvent(
    @EventTag(key = "ApplicationAggregate") UUID applicationId,
    long applicationVersion,
    long applicationDataVersion,
    String overallDecision,
    AutoGrantedState autoGranted,
    UUID caseworkerId,
    Instant occurredAt) {

  /** Supports historical events created before decision actors were persisted explicitly. */
  public ApplicationDecisionMadeEvent(
      UUID applicationId,
      long applicationVersion,
      long applicationDataVersion,
      String overallDecision,
      AutoGrantedState autoGranted,
      Instant occurredAt) {
    this(
        applicationId,
        applicationVersion,
        applicationDataVersion,
        overallDecision,
        autoGranted,
        null,
        occurredAt);
  }
}
