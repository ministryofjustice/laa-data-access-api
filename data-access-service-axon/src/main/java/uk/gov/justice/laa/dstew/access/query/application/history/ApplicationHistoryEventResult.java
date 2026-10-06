package uk.gov.justice.laa.dstew.access.query.application.history;

import java.time.Instant;
import java.util.UUID;

/** Query-specific immutable record for a single application-history event. */
public record ApplicationHistoryEventResult(
    UUID applicationId,
    String eventType,
    Instant occurredAt,
    String serviceName,
    String eventDescription,
    UUID caseworkerId) {}
