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
import uk.gov.justice.laa.dstew.access.eventcontent.RetainedField;

/**
 * Event raised when a terminal decision is made against a PriorAuthority submission. The decision
 * details and raw request are erasable content; the outcome and data version are kept.
 */
@Event(namespace = "access", name = "PriorAuthorityDecisionMade")
public record PriorAuthorityDecisionMadeEvent(
    @RetainedField @EventTag(key = "PriorAuthorityAggregate") UUID priorAuthorityId,
    @RetainedField UUID applicationId,
    @RetainedField String priorAuthorityType,
    @RetainedField long dataVersion,
    @RetainedField String overallDecision,
    Erasable<String> decisionJustification,
    Erasable<BigDecimal> amountGranted,
    Erasable<Instant> dateGranted,
    @RetainedField Instant occurredAt,
    Erasable<ExpertFeeInformation> expertFee,
    Erasable<DisbursementInformation> disbursementInformation,
    Erasable<ApportionmentInformation> apportionmentInformation,
    Erasable<String> serialisedRequest)
    implements GenesisScoped {

  /** Treats absent content as erased, as in an erased content row or a kept-fields-only read. */
  public PriorAuthorityDecisionMadeEvent {
    decisionJustification = Erasable.orErased(decisionJustification);
    amountGranted = Erasable.orErased(amountGranted);
    dateGranted = Erasable.orErased(dateGranted);
    expertFee = Erasable.orErased(expertFee);
    disbursementInformation = Erasable.orErased(disbursementInformation);
    apportionmentInformation = Erasable.orErased(apportionmentInformation);
    serialisedRequest = Erasable.orErased(serialisedRequest);
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
      Instant occurredAt,
      ExpertFeeInformation expertFee,
      DisbursementInformation disbursementInformation,
      ApportionmentInformation apportionmentInformation,
      String serialisedRequest) {
    this(
        priorAuthorityId,
        applicationId,
        priorAuthorityType,
        dataVersion,
        overallDecision,
        Erasable.of(decisionJustification),
        Erasable.of(amountGranted),
        Erasable.of(dateGranted),
        occurredAt,
        Erasable.of(expertFee),
        Erasable.of(disbursementInformation),
        Erasable.of(apportionmentInformation),
        Erasable.of(serialisedRequest));
  }

  /** Creates the event without cost adjustments or the raw request. */
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
        decisionJustification,
        amountGranted,
        dateGranted,
        occurredAt,
        null,
        null,
        null,
        null);
  }

  @Override
  public Set<UUID> genesisIds() {
    return GenesisScoped.ids(applicationId);
  }
}
