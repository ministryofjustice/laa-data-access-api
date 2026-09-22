package uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.modelling.annotation.TargetEntityId;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.DisbursementInformation;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.ExpertFeeInformation;

/** Requests a terminal decision against an existing PriorAuthority submission. */
@Command(routingKey = "priorAuthorityId")
public record MakePriorAuthorityDecisionCommand(
    @TargetEntityId UUID priorAuthorityId,
    UUID caseworkerId,
    long expectedPriorAuthorityVersion,
    String overallDecision,
    String decisionJustification,
    BigDecimal amountGranted,
    ExpertFeeInformation expertFee,
    DisbursementInformation disbursementInformation,
    ApportionmentInformation apportionmentInformation,
    Instant dateGranted,
    String serialisedRequest,
    Instant occurredAt) {}
