package uk.gov.justice.laa.dstew.access.document;

import java.time.Instant;
import java.util.UUID;
import lombok.With;

/** Filename-free, replayable document metadata shared by application types. */
@With
public record DocumentMetadata(
    UUID documentId,
    String documentType,
    Instant uploadedAt,
    Long size,
    String contentType,
    String checksum,
    String sourceService,
    boolean deleted) {}
