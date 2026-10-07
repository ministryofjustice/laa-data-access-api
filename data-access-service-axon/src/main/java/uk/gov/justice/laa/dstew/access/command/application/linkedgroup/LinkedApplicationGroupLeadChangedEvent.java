package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;

/** Emitted when the lead of a linked application group changes. */
@Event
public record LinkedApplicationGroupLeadChangedEvent(
    @EventTag(key = "LinkedApplicationGroupAggregate") UUID groupId,
    UUID previousLeadApplicationId,
    UUID newLeadApplicationId,
    long groupVersion,
    Instant occurredAt) {}
