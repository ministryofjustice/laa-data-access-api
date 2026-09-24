package uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;

/** Event raised when a terminal decision is made against a PriorAuthority submission. */
@Event
public record PriorAuthorityDecisionMadeEvent(
    @EventTag(key = "PriorAuthorityAggregate") UUID priorAuthorityId,
    UUID applicationId,
    String priorAuthorityType,
    long dataVersion,
    String overallDecision,
    String decisionJustification,
    BigDecimal amountGranted,
    Instant dateGranted,
    Instant occurredAt) {}
