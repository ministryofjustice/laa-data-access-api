package uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;
import org.jspecify.annotations.Nullable;

/**
 * The application took back its assignment on leaving a linked group; {@code caseworkerId} is null
 * unless it was an active manual work item.
 */
@Event
public record ApplicationAssignmentReclaimedFromGroupEvent(
    @EventTag(key = "ApplicationAggregate") UUID applicationId,
    UUID groupId,
    @Nullable UUID caseworkerId,
    long assignmentVersion,
    Instant occurredAt) {}
