package uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;

/** A linked group's shared assignment was cleared. */
@Event
public record LinkedGroupUnassignedEvent(
    @EventTag(key = "LinkedApplicationGroupAggregate") UUID groupId,
    long assignmentVersion,
    Instant occurredAt) {}
