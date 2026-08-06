package uk.gov.justice.laa.dstew.access.command.workitem;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;

/** Thin event recording a WorkItem unassignment. */
@Event
public record WorkItemUnassigned(
    @EventTag(key = "WorkItemAggregate") UUID workItemId,
    String serialisedRequest,
    String eventDescription,
    Instant occurredAt) {}
