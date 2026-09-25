package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import java.time.Instant;
import java.util.UUID;
import lombok.With;

/** Event-sourced technical facts for a prior-authority upload. */
@With
public record UploadedDocumentData(
    UUID documentId,
    Long size,
    String fileType,
    String contentType,
    String sourceService,
    String documentType,
    Instant uploadedAt,
    Instant deletedAt) {}
