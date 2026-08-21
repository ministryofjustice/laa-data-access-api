package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;

/** Thin event establishing the non-sensitive initial state of a PriorAuthority aggregate. */
@Event
public record PriorAuthorityCreatedEvent(
    @EventTag(key = "PriorAuthorityAggregate") UUID submissionId,
    UUID applicationId,
    long dataVersion,
    String requestFingerprint,
    String status,
    int schemaVersion,
    Instant occurredAt) {}
