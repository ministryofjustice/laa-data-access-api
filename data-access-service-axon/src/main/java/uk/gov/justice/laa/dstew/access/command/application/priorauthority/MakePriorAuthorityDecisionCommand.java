package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.modelling.annotation.TargetEntityId;

/** Requests a terminal decision against an existing PriorAuthority submission. */
@Command(routingKey = "submissionId")
public record MakePriorAuthorityDecisionCommand(
    @TargetEntityId UUID submissionId,
    String overallDecision,
    String serialisedRequest,
    String decisionJustification,
    Instant occurredAt) {}
