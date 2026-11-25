package uk.gov.justice.laa.dstew.access.command.application.decision;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;
import org.jspecify.annotations.Nullable;
import uk.gov.justice.laa.dstew.access.command.GenesisScoped;
import uk.gov.justice.laa.dstew.access.command.application.AutoGrantedState;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationMeritsDecision;
import uk.gov.justice.laa.dstew.access.eventcontent.RedactedEventString;
import uk.gov.justice.laa.dstew.access.eventcontent.RetainedField;

/**
 * Records an Application decision. The merits decisions for the proceedings it covers, the
 * certificate and the event description are erasable content. Kept fields: {@code
 * decisionRequestFingerprint}, so that a retried automatic grant can be recognised, and {@code
 * proceedingDecisions}, the decision value per proceeding, which is not personal data and informs
 * retention schedules.
 */
@Event(namespace = "access", name = "ApplicationDecisionMade")
public record ApplicationDecisionMadeEvent(
    @RetainedField @EventTag(key = "ApplicationAggregate") UUID applicationId,
    @RetainedField long applicationVersion,
    @RetainedField long applicationDataVersion,
    @RetainedField(nullable = true) String overallDecision,
    @RetainedField(nullable = true) AutoGrantedState autoGranted,
    @RetainedField Instant occurredAt,
    @RetainedField(nullable = true) String decisionRequestFingerprint,
    @RetainedField Map<UUID, String> proceedingDecisions,
    @Nullable Map<UUID, ApplicationMeritsDecision> meritsDecisions,
    @Nullable Map<String, Object> certificate,
    @Nullable String eventDescription)
    implements GenesisScoped {

  /** Copies the proceeding decisions, normalising missing ones to none. */
  public ApplicationDecisionMadeEvent {
    proceedingDecisions =
        proceedingDecisions == null
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(proceedingDecisions));
  }

  /** Creates the event from its content, keeping each proceeding's decision value. */
  public ApplicationDecisionMadeEvent(
      UUID applicationId,
      long applicationVersion,
      long applicationDataVersion,
      String overallDecision,
      AutoGrantedState autoGranted,
      Instant occurredAt,
      String decisionRequestFingerprint,
      @Nullable Map<UUID, ApplicationMeritsDecision> meritsDecisions,
      @Nullable Map<String, Object> certificate,
      @Nullable String eventDescription) {
    this(
        applicationId,
        applicationVersion,
        applicationDataVersion,
        overallDecision,
        autoGranted,
        occurredAt,
        decisionRequestFingerprint,
        proceedingDecisions(meritsDecisions),
        meritsDecisions,
        certificate,
        eventDescription);
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
        null,
        null,
        null);
  }

  private static Map<UUID, String> proceedingDecisions(
      @Nullable Map<UUID, ApplicationMeritsDecision> meritsDecisions) {
    Map<UUID, String> decisions = new LinkedHashMap<>();
    if (meritsDecisions != null) {
      meritsDecisions.forEach((id, merits) -> decisions.put(id, merits.decision()));
    }
    return decisions;
  }

  @Override
  public Set<UUID> genesisIds() {
    return GenesisScoped.ids(applicationId);
  }

  @Override
  public String toString() {
    return RedactedEventString.of(this);
  }
}
