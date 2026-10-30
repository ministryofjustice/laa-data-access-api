package uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;

/** A linked group took ownership of the application's assignment. */
@Event
public record ApplicationAssignmentDelegatedToGroupEvent(
    @EventTag(key = "ApplicationAggregate") UUID applicationId, UUID groupId, Instant occurredAt) {}
