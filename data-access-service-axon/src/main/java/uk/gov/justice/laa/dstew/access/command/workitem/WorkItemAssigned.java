package uk.gov.justice.laa.dstew.access.command.workitem;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;

/** Thin event recording a WorkItem assignment. */
@Event
public record WorkItemAssigned(
    @EventTag(key = "WorkItemAggregate") UUID workItemId,
    UUID caseworkerId,
    String serialisedRequest,
    String eventDescription,
    Instant occurredAt,
    boolean fanOut) {}
