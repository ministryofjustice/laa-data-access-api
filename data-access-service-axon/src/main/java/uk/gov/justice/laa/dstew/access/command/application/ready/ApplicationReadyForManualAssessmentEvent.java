package uk.gov.justice.laa.dstew.access.command.application.ready;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;

/** Thin event recording the immutable data version that contains {@code autoGrant=false}. */
@Event
public record ApplicationReadyForManualAssessmentEvent(
    @EventTag(key = "ApplicationAggregate") UUID applicationId,
    long applicationVersion,
    long applicationDataVersion,
    Instant occurredAt) {}
