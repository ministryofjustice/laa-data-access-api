package uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;
import uk.gov.justice.laa.dstew.access.eventcontent.Erasable;

/** Event raised when a terminal decision is made against a PriorAuthority submission. */
@Event(namespace = "access", name = "PriorAuthorityDecisionMade")
public record PriorAuthorityDecisionMadeEvent(
    @EventTag(key = "PriorAuthorityAggregate") UUID priorAuthorityId,
    UUID applicationId,
    String priorAuthorityType,
    long dataVersion,
    String overallDecision,
    Erasable<String> decisionJustification,
    Erasable<BigDecimal> amountGranted,
    Erasable<Instant> dateGranted,
    Instant occurredAt) {

  /** Treats absent content as erased so a stored event without its content row still loads. */
  public PriorAuthorityDecisionMadeEvent {
    decisionJustification = Erasable.orErased(decisionJustification);
    amountGranted = Erasable.orErased(amountGranted);
    dateGranted = Erasable.orErased(dateGranted);
  }

  /** Creates the event from plain content values. */
  public PriorAuthorityDecisionMadeEvent(
      UUID priorAuthorityId,
      UUID applicationId,
      String priorAuthorityType,
      long dataVersion,
      String overallDecision,
      String decisionJustification,
      BigDecimal amountGranted,
      Instant dateGranted,
      Instant occurredAt) {
    this(
        priorAuthorityId,
        applicationId,
        priorAuthorityType,
        dataVersion,
        overallDecision,
        Erasable.of(decisionJustification),
        Erasable.of(amountGranted),
        Erasable.of(dateGranted),
        occurredAt);
  }
}
