package uk.gov.justice.laa.dstew.access.command.application.decision;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;
import uk.gov.justice.laa.dstew.access.command.application.AutoGrantedState;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationMeritsDecision;
import uk.gov.justice.laa.dstew.access.eventcontent.Erasable;

/**
 * Records an Application decision. The merits decisions for the proceedings it covers, the
 * certificate, the request and its description are erasable content. Kept fields: {@code
 * decisionRequestFingerprint}, so that a retried automatic grant can be recognised, and {@code
 * proceedingDecisions}, the decision value per proceeding, which is not personal data and informs
 * retention schedules.
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
    Map<UUID, String> proceedingDecisions,
    Erasable<Map<UUID, ApplicationMeritsDecision>> meritsDecisions,
    Erasable<Map<String, Object>> certificate,
    Erasable<String> serialisedRequest,
    Erasable<String> eventDescription) {

  /** Normalises missing content to erased. */
  public ApplicationDecisionMadeEvent {
    proceedingDecisions =
        proceedingDecisions == null
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(proceedingDecisions));
    meritsDecisions = Erasable.orErased(meritsDecisions);
    certificate = Erasable.orErased(certificate);
    serialisedRequest = Erasable.orErased(serialisedRequest);
    eventDescription = Erasable.orErased(eventDescription);
  }

  /** Creates the event from plain content values, keeping each proceeding's decision value. */
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
        proceedingDecisions(meritsDecisions),
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

  private static Map<UUID, String> proceedingDecisions(
      Map<UUID, ApplicationMeritsDecision> meritsDecisions) {
    Map<UUID, String> decisions = new LinkedHashMap<>();
    if (meritsDecisions != null) {
      meritsDecisions.forEach((id, merits) -> decisions.put(id, merits.decision()));
    }
    return decisions;
  }
}
