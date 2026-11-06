package uk.gov.justice.laa.dstew.access.command.application.decision;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;
import uk.gov.justice.laa.dstew.access.command.application.AutoGrantedState;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationMeritsDecision;
import uk.gov.justice.laa.dstew.access.eventcontent.Erasable;

/**
 * Records an Application decision. The merits decisions for the proceedings it covers, the
 * certificate, the request and its description are erasable content; {@code
 * decisionRequestFingerprint} is kept so that a retried automatic grant can be recognised.
 */
@Event(namespace = "access", name = "ApplicationDecisionMade")
public record ApplicationDecisionMadeEvent(
    @EventTag(key = "ApplicationAggregate") UUID applicationId,
    long applicationVersion,
    long applicationDataVersion,
    String overallDecision,
    AutoGrantedState autoGranted,
    Instant occurredAt,
    String decisionRequestFingerprint,
    Erasable<Map<UUID, ApplicationMeritsDecision>> meritsDecisions,
    Erasable<Map<String, Object>> certificate,
    Erasable<String> serialisedRequest,
    Erasable<String> eventDescription) {

  /** Normalises missing content to erased. */
  public ApplicationDecisionMadeEvent {
    meritsDecisions = Erasable.orErased(meritsDecisions);
    certificate = Erasable.orErased(certificate);
    serialisedRequest = Erasable.orErased(serialisedRequest);
    eventDescription = Erasable.orErased(eventDescription);
  }

  /** Creates the event from plain content values. */
  public ApplicationDecisionMadeEvent(
      UUID applicationId,
      long applicationVersion,
      long applicationDataVersion,
      String overallDecision,
      AutoGrantedState autoGranted,
      Instant occurredAt,
      String decisionRequestFingerprint,
      Map<UUID, ApplicationMeritsDecision> meritsDecisions,
      Map<String, Object> certificate,
      String serialisedRequest,
      String eventDescription) {
    this(
        applicationId,
        applicationVersion,
        applicationDataVersion,
        overallDecision,
        autoGranted,
        occurredAt,
        decisionRequestFingerprint,
        Erasable.of(meritsDecisions),
        Erasable.of(certificate),
        Erasable.of(serialisedRequest),
        Erasable.of(eventDescription));
  }

  /** Creates the event without decision content. */
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
        occurredAt,
        null,
        (Map<UUID, ApplicationMeritsDecision>) null,
        null,
        null,
        null);
  }
}
