package uk.gov.justice.laa.dstew.access.document;

import java.time.Instant;
import java.util.UUID;

/** Internal result of a completed document upload, independent of application type. */
public record DocumentUploadResult(
    UUID documentId,
    String fileName,
    String fileType,
    String contentType,
    long size,
    Instant uploadedAt,
    String sourceService,
    String checksum) {}
