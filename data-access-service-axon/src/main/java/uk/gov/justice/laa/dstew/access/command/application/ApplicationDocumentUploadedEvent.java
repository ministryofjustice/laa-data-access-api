package uk.gov.justice.laa.dstew.access.command.application;

import java.time.Instant;
import java.util.UUID;

/** Persisted event emitted after an application document upload completes. */
public record ApplicationDocumentUploadedEvent(
    UUID applicationId,
    UUID documentId,
    String documentType,
    Instant uploadedAt,
    Long size,
    String contentType,
    String checksum,
    String sourceService) {}
