package uk.gov.justice.laa.dstew.access.command.application.document;

import java.time.Instant;
import java.util.UUID;

/** Internal result of a completed application document upload. */
public record UploadApplicationDocumentResult(
    UUID documentId,
    String fileName,
    String fileType,
    String contentType,
    long size,
    Instant uploadedAt,
    String sourceService,
    String checksum) {}
