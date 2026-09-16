package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import java.time.Instant;
import java.util.UUID;

/** Persisted event emitted when a Prior Authority document type is set or replaced. */
public record PriorAuthorityDocumentTypeUpdatedEvent(
    UUID priorAuthorityId, UUID documentId, String documentType, Instant occurredAt) {}
