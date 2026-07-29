package uk.gov.justice.laa.dstew.access.command.application;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;

/** Records that an Application belongs to a lead Application's linked group. */
@Event
public record ApplicationLinkedEvent(
    @EventTag(key = "ApplicationAggregate") UUID applicationId,
    UUID leadApplicationId,
    Instant occurredAt) {}
