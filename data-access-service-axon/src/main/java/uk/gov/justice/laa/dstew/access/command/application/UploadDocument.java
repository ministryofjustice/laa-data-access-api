package uk.gov.justice.laa.dstew.access.command.application;

import java.time.Instant;
import java.util.UUID;

/** Replayable metadata for a document uploaded to an application. */
public record UploadDocument(
    UUID documentId,
    String documentType,
    Instant uploadedAt,
    Long size,
    String contentType,
    String checksum,
    String sourceService) {}
