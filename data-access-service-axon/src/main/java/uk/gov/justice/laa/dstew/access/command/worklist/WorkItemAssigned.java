package uk.gov.justice.laa.dstew.access.command.worklist;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;

/** Thin replayable event recording a direct work-item assignment. */
@Event
public record WorkItemAssigned(
    WorkItemId workItemId,
    @EventTag(key = "ApplicationAggregate") UUID applicationId,
    @EventTag(key = "PriorAuthorityAggregate") UUID priorAuthorityId,
    long itemVersion,
    long dataVersion,
    long assignmentVersion,
    UUID caseworkerId,
    Instant occurredAt) {}


