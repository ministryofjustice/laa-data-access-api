package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;

/**
 * Emitted when an application is added to an already-existing {@link
 * LinkedApplicationGroupAggregate}.
 */
@Event
public record MemberAddedToGroupEvent(
    @EventTag(key = "LinkedApplicationGroupAggregate") UUID groupId,
    UUID memberId,
    Instant occurredAt) {}
