package uk.gov.justice.laa.dstew.access.command.application.priorauthority.document;

import java.time.Instant;
import java.util.UUID;

/** Internal result of a completed prior-authority document upload. */
public record UploadPriorAuthorityDocumentResult(
    UUID documentId,
    String documentType,
    String fileName,
    String fileType,
    String contentType,
    long size,
    Instant uploadedAt,
    String sourceService,
    String checksum) {}
