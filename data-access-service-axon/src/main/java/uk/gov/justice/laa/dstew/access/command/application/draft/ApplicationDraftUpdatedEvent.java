package uk.gov.justice.laa.dstew.access.command.application.draft;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;

/**
 * Thin, PII-free pointer event — with no draft content — for an Application draft-body update. It
 * exists to seal the write through the aggregate's stream, mirroring the equivalent Prior Authority
 * draft-updated event.
 */
@Event
public record ApplicationDraftUpdatedEvent(
    @EventTag(key = "ApplicationAggregate") UUID applicationId, Instant occurredAt) {}
