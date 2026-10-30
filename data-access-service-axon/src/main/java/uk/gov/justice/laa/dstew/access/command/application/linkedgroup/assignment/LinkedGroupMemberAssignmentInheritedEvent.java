package uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;

/**
 * A member that became ready for manual assessment took the group's caseworker; the group's state
 * is unchanged.
 */
@Event
public record LinkedGroupMemberAssignmentInheritedEvent(
    @EventTag(key = "LinkedApplicationGroupAggregate") UUID groupId,
    UUID memberApplicationId,
    UUID caseworkerId,
    long assignmentVersion,
    Instant occurredAt) {}
