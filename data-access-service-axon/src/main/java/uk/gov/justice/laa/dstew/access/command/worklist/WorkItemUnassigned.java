package uk.gov.justice.laa.dstew.access.command.worklist;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;

/** Thin replayable event returning a direct work item to the open list. */
@Event
public record WorkItemUnassigned(
    @EventTag UUID workItemId,
    WorkItemType workItemType,
    long itemVersion,
    long dataVersion,
    long assignmentVersion,
    String eventDescription,
    Instant occurredAt) {}
