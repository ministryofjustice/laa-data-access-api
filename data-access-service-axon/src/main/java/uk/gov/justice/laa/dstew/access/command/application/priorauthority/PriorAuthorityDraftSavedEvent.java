package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;

/** Event produced when a Prior Authority draft is saved or updated. */
@Event
public record PriorAuthorityDraftSavedEvent(
    @EventTag(key = "PriorAuthorityAggregate") UUID submissionId,
    UUID applicationId,
    long dataVersion,
    String requestFingerprint,
    String status,
    int schemaVersion,
    Instant occurredAt) {}
