package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;

/** Event recording a terminal decision against a PriorAuthority submission. */
@Event
public record PriorAuthorityDecisionRecordedEvent(
    @EventTag(key = "PriorAuthorityAggregate") UUID submissionId,
    UUID applicationId,
    String priorAuthorityType,
    long dataVersion,
    String status,
    String decisionJustification,
    Double amountGranted,
    Instant dateGranted,
    Instant occurredAt) {}
