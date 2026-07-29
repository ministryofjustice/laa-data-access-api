package uk.gov.justice.laa.dstew.access.command.application.note;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;

/** Records that a note was appended to an Application's immutable data. */
@Event
public record NoteCreatedEvent(
    @EventTag(key = "ApplicationAggregate") UUID applicationId,
    long applicationDataVersion,
    Instant occurredAt) {}
