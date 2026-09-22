package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import java.time.Instant;
import java.util.UUID;

/** Persisted event emitted when a document is removed from a prior-authority draft. */
public record PriorAuthorityDocumentDeletedEvent(
    UUID priorAuthorityId, UUID documentId, Instant deletedAt, UUID parentApplicationId) {}
