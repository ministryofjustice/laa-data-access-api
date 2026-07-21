package uk.gov.justice.laa.dstew.access.command.application.assignment;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;

/** Thin event recording removal of an application's caseworker assignment. */
@Event
public record ApplicationUnassignedFromCaseworkerEvent(
    @EventTag(key = "ApplicationAggregate") UUID applicationId,
    long applicationVersion,
    long applicationDataVersion,
    Instant occurredAt) {}
