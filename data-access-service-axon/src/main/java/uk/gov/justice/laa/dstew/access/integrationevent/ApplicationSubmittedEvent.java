package uk.gov.justice.laa.dstew.access.integrationevent;

import java.time.Instant;
import java.util.UUID;

/** Versioned, non-sensitive integration event emitted when an Application becomes submitted. */
public record ApplicationSubmittedEvent(
    String eventType,
    int schemaVersion,
    UUID eventId,
    Instant occurredAt,
    String source,
    String correlationId,
    ApplicationSubmittedData data) {}
