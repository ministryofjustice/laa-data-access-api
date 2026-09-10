package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import java.time.Instant;
import java.util.UUID;

/** Persisted event emitted when a prior-authority document is uploaded and finalised. */
public record PriorAuthorityDocumentUploadedEvent(
    UUID priorAuthorityId,
    UUID documentId,
    Instant uploadedAt,
    Long size,
    String contentType,
    String checksum,
    UUID parentApplicationId) {}
