package uk.gov.justice.laa.dstew.access.document;

import com.fasterxml.jackson.annotation.JsonInclude;
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
    boolean deleted,
    @JsonInclude(JsonInclude.Include.NON_NULL) String fileSuffix) {

  /** Creates historical document metadata whose storage suffix is unknown. */
  public DocumentMetadata(
      UUID documentId,
      String documentType,
      Instant uploadedAt,
      Long size,
      String contentType,
      String checksum,
      String sourceService,
      boolean deleted) {
    this(
        documentId,
        documentType,
        uploadedAt,
        size,
        contentType,
        checksum,
        sourceService,
        deleted,
        null);
  }
}
