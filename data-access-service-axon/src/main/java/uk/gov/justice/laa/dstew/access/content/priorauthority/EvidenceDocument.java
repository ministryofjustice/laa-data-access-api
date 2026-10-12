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
    String checksum,
    String fileSuffix) {

  /** Creates a hydrated document for historical metadata without a recorded suffix. */
  public EvidenceDocument(
      UUID documentId,
      String documentType,
      String fileName,
      String fileType,
      String mediaType,
      Long size,
      Instant uploadedAt,
      String sourceService,
      String checksum) {
    this(
        documentId,
        documentType,
        fileName,
        fileType,
        mediaType,
        size,
        uploadedAt,
        sourceService,
        checksum,
        null);
  }

  /** Uses persisted storage metadata, with an original-filename fallback for historical uploads. */
  public String storageFilename() {
    return fileSuffix == null ? fileName : documentId + fileSuffix;
  }
}
