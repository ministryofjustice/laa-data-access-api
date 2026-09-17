package uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.modelling.annotation.TargetEntityId;

/** Requests a terminal decision against an existing PriorAuthority submission. */
@Command(routingKey = "priorAuthorityId")
public record MakePriorAuthorityDecisionCommand(
    @TargetEntityId UUID priorAuthorityId,
    UUID caseworkerId,
    long expectedPriorAuthorityVersion,
    String overallDecision,
    String decisionJustification,
    BigDecimal amountGranted,
    uk.gov.justice.laa.dstew.access.command.application.priorauthority.ExpertFeeInformation
        expertFee,
    uk.gov.justice.laa.dstew.access.command.application.priorauthority.DisbursementInformation
        disbursementInformation,
    ApportionmentInformation apportionmentInformation,
    Instant dateGranted,
    String serialisedRequest,
    Instant occurredAt) {}
