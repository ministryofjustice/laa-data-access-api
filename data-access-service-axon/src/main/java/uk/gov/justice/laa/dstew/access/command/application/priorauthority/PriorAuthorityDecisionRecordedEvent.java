package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;

/** Thin event recording a terminal decision against a PriorAuthority submission. */
@Event
public record PriorAuthorityDecisionRecordedEvent(
    @EventTag(key = "PriorAuthorityAggregate") UUID submissionId,
    UUID applicationId,
    long dataVersion,
    String status,
    Instant occurredAt) {}
