package uk.gov.justice.laa.dstew.access.content.priorauthority;

import java.time.Instant;
import java.util.UUID;
import lombok.With;

/** Metadata for a document uploaded against a prior-authority draft. */
@With
public record PriorAuthorityDocument(
    UUID documentId,
    String documentType,
    String fileName,
    String fileType,
    String mediaType,
    Long size,
    Instant uploadedAt,
    String sourceService,
    String checksum) {}
