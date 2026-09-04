package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;

/** Event produced when a Prior Authority draft is first created. */
@Event
public record PriorAuthorityDraftStartedEvent(
    @EventTag(key = "PriorAuthorityAggregate") UUID priorAuthorityId,
    UUID applicationId,
    String requestFingerprint,
    int schemaVersion,
    Instant occurredAt) {}
