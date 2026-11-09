package uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;
import uk.gov.justice.laa.dstew.access.command.GenesisScoped;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.DisbursementInformation;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.ExpertFeeInformation;
import uk.gov.justice.laa.dstew.access.eventcontent.Erasable;

/** Event raised when a terminal decision is made against a PriorAuthority submission. */
@Event(namespace = "access", name = "PriorAuthorityDecisionMade")
public record PriorAuthorityDecisionMadeEvent(
    @EventTag(key = "PriorAuthorityAggregate") UUID priorAuthorityId,
    UUID applicationId,
    String priorAuthorityType,
    String overallDecision,
    Erasable<String> decisionJustification,
    Erasable<BigDecimal> amountGranted,
    Erasable<Instant> dateGranted,
    Erasable<ExpertFeeInformation> expertFee,
    Erasable<DisbursementInformation> disbursementInformation,
    Erasable<ApportionmentInformation> apportionmentInformation,
    Instant occurredAt)
    implements GenesisScoped {

  /** Treats absent content as erased so a stored event without its content row still loads. */
  public PriorAuthorityDecisionMadeEvent {
    decisionJustification = Erasable.orErased(decisionJustification);
    amountGranted = Erasable.orErased(amountGranted);
    dateGranted = Erasable.orErased(dateGranted);
    expertFee = Erasable.orErased(expertFee);
    disbursementInformation = Erasable.orErased(disbursementInformation);
    apportionmentInformation = Erasable.orErased(apportionmentInformation);
  }

  /** Creates the event from plain content values. */
  public PriorAuthorityDecisionMadeEvent(
      UUID priorAuthorityId,
      UUID applicationId,
      String priorAuthorityType,
      String overallDecision,
      String decisionJustification,
      BigDecimal amountGranted,
      Instant dateGranted,
      ExpertFeeInformation expertFee,
      DisbursementInformation disbursementInformation,
      ApportionmentInformation apportionmentInformation,
      Instant occurredAt) {
    this(
        priorAuthorityId,
        applicationId,
        priorAuthorityType,
        overallDecision,
        Erasable.of(decisionJustification),
        Erasable.of(amountGranted),
        Erasable.of(dateGranted),
        Erasable.of(expertFee),
        Erasable.of(disbursementInformation),
        Erasable.of(apportionmentInformation),
        occurredAt);
  }

  /** Creates the event from plain content values without cost adjustments. */
  public PriorAuthorityDecisionMadeEvent(
      UUID priorAuthorityId,
      UUID applicationId,
      String priorAuthorityType,
      String overallDecision,
      String decisionJustification,
      BigDecimal amountGranted,
      Instant dateGranted,
      Instant occurredAt) {
    this(
        priorAuthorityId,
        applicationId,
        priorAuthorityType,
        overallDecision,
        decisionJustification,
        amountGranted,
        dateGranted,
        null,
        null,
        null,
        occurredAt);
  }

  @Override
  public Set<UUID> genesisIds() {
    return GenesisScoped.ids(applicationId);
  }
}
