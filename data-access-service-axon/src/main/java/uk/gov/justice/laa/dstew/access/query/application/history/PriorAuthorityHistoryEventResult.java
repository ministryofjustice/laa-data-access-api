package uk.gov.justice.laa.dstew.access.query.application.history;

import java.time.Instant;

/** Query-specific immutable record for a single prior-authority event. */
public record PriorAuthorityHistoryEventResult(
    String eventType, Instant occurredAt, String serviceName, String eventDescription) {}
