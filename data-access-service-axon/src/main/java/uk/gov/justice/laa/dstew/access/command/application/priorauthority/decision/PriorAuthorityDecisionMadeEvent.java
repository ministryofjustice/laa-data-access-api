package uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;
import org.jspecify.annotations.Nullable;
import uk.gov.justice.laa.dstew.access.command.GenesisScoped;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.DisbursementInformation;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.ExpertFeeInformation;
import uk.gov.justice.laa.dstew.access.eventcontent.RedactedEventString;
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
    @Nullable String decisionJustification,
    @Nullable BigDecimal amountGranted,
    @Nullable Instant dateGranted,
    @RetainedField Instant occurredAt,
    @Nullable ExpertFeeInformation expertFee,
    @Nullable DisbursementInformation disbursementInformation,
    @Nullable ApportionmentInformation apportionmentInformation,
    @Nullable String serialisedRequest)
    implements GenesisScoped {

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

  @Override
  public String toString() {
    return RedactedEventString.of(this);
  }
}
