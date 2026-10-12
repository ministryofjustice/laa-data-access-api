package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;

/** Emitted when a non-lead application leaves a linked group that remains active. */
@Event
public record MemberRemovedFromGroupEvent(
    @EventTag(key = "LinkedApplicationGroupAggregate") UUID groupId,
    UUID leadApplicationId,
    UUID memberId,
    long groupVersion,
    Instant occurredAt)
    implements LinkedGroupMemberRemoval {}
