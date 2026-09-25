package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;

/** Persisted event emitted when a document is removed from a prior-authority draft. */
public record PriorAuthorityDocumentDeletedEvent(
    @EventTag(key = "PriorAuthorityAggregate") UUID priorAuthorityId,
    UUID documentId,
    Instant deletedAt,
    UUID parentApplicationId) {}
