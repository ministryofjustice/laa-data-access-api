package uk.gov.justice.laa.dstew.access.query.application.history;

import java.time.Instant;
import java.util.UUID;

/** Query-specific immutable record for a single prior-authority event. */
public record PriorAuthorityHistoryEventResult(
    String eventType,
    Instant occurredAt,
    String serviceName,
    String eventDescription,
    UUID caseworkerId) {}
