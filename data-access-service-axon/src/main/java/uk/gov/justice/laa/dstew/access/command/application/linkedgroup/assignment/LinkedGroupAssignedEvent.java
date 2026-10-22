package uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;

/** A linked group's shared assignment was given to a caseworker. */
@Event
public record LinkedGroupAssignedEvent(
    @EventTag(key = "LinkedApplicationGroupAggregate") UUID groupId,
    UUID caseworkerId,
    long assignmentVersion,
    Instant occurredAt) {}
