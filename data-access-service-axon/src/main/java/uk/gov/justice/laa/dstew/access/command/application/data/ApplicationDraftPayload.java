package uk.gov.justice.laa.dstew.access.command.application.data;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;
import uk.gov.justice.laa.dstew.access.model.PotentialDuplicate;

/**
 * Contains the content of an Application that has not yet been submitted.
 *
 * <p>The application content is stored as a raw map and parsed into typed fields when the draft is
 * submitted.
 */
@ExcludeFromGeneratedCodeCoverage
public record ApplicationDraftPayload(
    String status,
    String laaReference,
    Map<String, Object> applicationContent,
    String serialisedRequest,
    List<PotentialDuplicate> potentialDuplicates,
    Map<UUID, String> documentFilenames,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, Object> metadata) {

  /** Normalizes draft rows that predate filename and metadata persistence. */
  public ApplicationDraftPayload {
    documentFilenames = documentFilenames == null ? Map.of() : Map.copyOf(documentFilenames);
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }

  /** Creates draft content without document filenames or metadata for existing callers. */
  public ApplicationDraftPayload(
      String status,
      String laaReference,
      Map<String, Object> applicationContent,
      String serialisedRequest,
      List<PotentialDuplicate> potentialDuplicates) {
    this(
        status,
        laaReference,
        applicationContent,
        serialisedRequest,
        potentialDuplicates,
        Map.of(),
        Map.of());
  }

  /** Creates draft content without metadata for existing callers. */
  public ApplicationDraftPayload(
      String status,
      String laaReference,
      Map<String, Object> applicationContent,
      String serialisedRequest,
      List<PotentialDuplicate> potentialDuplicates,
      Map<UUID, String> documentFilenames) {
    this(
        status,
        laaReference,
        applicationContent,
        serialisedRequest,
        potentialDuplicates,
        documentFilenames,
        Map.of());
  }

  /** Returns draft content with the original filename recorded by document ID. */
  public ApplicationDraftPayload withDocumentFilename(UUID documentId, String originalFilename) {
    Map<UUID, String> updated = new HashMap<>(documentFilenames);
    updated.put(documentId, originalFilename);
    return new ApplicationDraftPayload(
        status,
        laaReference,
        applicationContent,
        serialisedRequest,
        potentialDuplicates,
        updated,
        metadata);
  }

  /** Returns draft content with the supplied envelope metadata replacing the existing metadata. */
  public ApplicationDraftPayload withMetadata(Map<String, Object> updatedMetadata) {
    return new ApplicationDraftPayload(
        status,
        laaReference,
        applicationContent,
        serialisedRequest,
        potentialDuplicates,
        documentFilenames,
        updatedMetadata);
  }
}
