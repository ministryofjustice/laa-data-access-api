package uk.gov.justice.laa.dstew.access.command.application.draft;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;

/**
 * Thin, PII-free pointer event marking that an Application draft has been started for the given ID.
 * Exists so the aggregate can guard against a second draft (or a direct create) being started for
 * the same ID, without carrying any draft content itself.
 */
@Event
public record ApplicationDraftStartedEvent(
    @EventTag(key = "ApplicationAggregate") UUID applicationId,
    int schemaVersion,
    Instant occurredAt) {}
