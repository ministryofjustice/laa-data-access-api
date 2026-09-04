package uk.gov.justice.laa.dstew.access.command.worklist;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;

/** Thin replayable event recording a direct work-item assignment. */
@Event
public record WorkItemAssigned(
    @EventTag UUID workItemId,
    WorkItemType workItemType,
    long itemVersion,
    long assignmentVersion,
    UUID caseworkerId,
    String eventDescription,
    Instant occurredAt) {}
