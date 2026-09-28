package uk.gov.justice.laa.dstew.access.command.application;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;

/** Persisted event emitted after an application document upload completes. */
@Event
public record ApplicationDocumentUploadedEvent(
    @EventTag(key = "ApplicationAggregate") UUID applicationId,
    UUID documentId,
    String documentType,
    Instant uploadedAt,
    Long size,
    String contentType,
    String checksum,
    String sourceService) {}
