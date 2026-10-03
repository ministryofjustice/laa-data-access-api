package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;

/** Persisted event emitted when a prior-authority document is uploaded and finalised. */
@Event
public record PriorAuthorityDocumentUploadedEvent(
    @EventTag(key = "PriorAuthorityAggregate") UUID priorAuthorityId,
    UUID documentId,
    Instant uploadedAt,
    Long size,
    String contentType,
    String checksum,
    UUID parentApplicationId,
    String sourceService) {}
