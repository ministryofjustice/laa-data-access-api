package uk.gov.justice.laa.dstew.access.command.application.draft;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;

/** Event indicating that an Application draft has been started for the given ID. */
@Event
public record ApplicationDraftStartedEvent(
    @EventTag(key = "ApplicationAggregate") UUID applicationId,
    int schemaVersion,
    String requestFingerprint,
    Instant occurredAt) {}
