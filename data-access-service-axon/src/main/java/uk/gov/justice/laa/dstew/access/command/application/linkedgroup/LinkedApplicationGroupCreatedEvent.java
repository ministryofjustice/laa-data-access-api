package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;

/**
 * Emitted by {@code LinkedApplicationGroupAggregate} once the group is fully initialised. Consumed
 * by projections to record group membership and derive {@code isLead} on each member's read model.
 */
@Event
public record LinkedApplicationGroupCreatedEvent(
    @EventTag(key = "LinkedApplicationGroupAggregate") UUID groupId,
    UUID leadApplicationId,
    List<UUID> memberApplicationIds,
    Instant occurredAt) {}
