package uk.gov.justice.laa.dstew.access.content.priorauthority;

import java.time.Instant;
import java.util.UUID;
import lombok.With;

/** Hydrated document metadata for downloads, independent of application type. */
@With
public record EvidenceDocument(
    UUID documentId,
    String documentType,
    String fileName,
    String fileType,
    String mediaType,
    Long size,
    Instant uploadedAt,
    String sourceService,
    String checksum) {}
