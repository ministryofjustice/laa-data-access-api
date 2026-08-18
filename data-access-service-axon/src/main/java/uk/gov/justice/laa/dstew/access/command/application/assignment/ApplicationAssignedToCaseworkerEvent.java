package uk.gov.justice.laa.dstew.access.command.application.assignment;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;

/** Thin event recording a caseworker assignment and its referenced sensitive-data version. */
@Event
public record ApplicationAssignedToCaseworkerEvent(
    @EventTag(key = "ApplicationAggregate") UUID applicationId,
    long applicationVersion,
    long applicationDataVersion,
    UUID caseworkerId,
    Instant occurredAt) {}
