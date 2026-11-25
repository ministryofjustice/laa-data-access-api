package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent;
import uk.gov.justice.laa.dstew.access.document.DocumentMetadata;

/** State object reconstructed by folding a PriorAuthority aggregate's event stream. */
@Getter
@NoArgsConstructor
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class PriorAuthorityState {
  UUID priorAuthorityId;
  UUID applicationId;
  String officeCode;
  long dataVersion;
  String requestFingerprint;
  boolean submitted;
  boolean decided;
  int schemaVersion;
  UUID caseworkerId;
  long assignmentVersion;
  String priorAuthorityType;
  String billingType;
  List<DocumentMetadata> uploadedDocuments = new ArrayList<>();

  @Getter(AccessLevel.NONE)
  @Nullable PriorAuthorityContent draftContent;

  Instant draftModifiedAt;
  // A null value means the filename has been erased; the key is kept.
  Map<UUID, @Nullable String> documentFilenames = new LinkedHashMap<>();

  @Getter(AccessLevel.NONE)
  @Nullable PriorAuthorityDataPayload submission;

  /**
   * Returns the open draft folded from the event stream: empty if the draft does not exist, has
   * been submitted, or its content has been erased.
   */
  public Optional<PriorAuthorityDataPayload> draft() {
    if (!hasOpenDraft()) {
      return Optional.empty();
    }
    return Optional.of(
        new PriorAuthorityDataPayload(
            priorAuthorityId,
            applicationId,
            draftContent,
            draftModifiedAt,
            null,
            officeCode,
            retainedFilenames()));
  }

  /**
   * Returns the submitted content at the current data version, folded from the submission and
   * decision events: empty if the Prior Authority has not been submitted or its submitted content
   * has been erased. It matches the latest {@code prior_authority_data} version, which is still
   * written for the existing read paths.
   */
  public Optional<PriorAuthorityDataPayload> submittedPayload() {
    if (priorAuthorityId == null || !submitted) {
      return Optional.empty();
    }
    return Optional.ofNullable(submission);
  }

  /** Command pre-check: return no draft body, and only the filename requested for SDS cleanup. */
  public PriorAuthorityDraftSummary draftSummary(UUID documentId) {
    if (!hasOpenDraft()) {
      return PriorAuthorityDraftSummary.NONE;
    }
    return new PriorAuthorityDraftSummary(applicationId, documentFilenames.get(documentId));
  }

  private boolean hasOpenDraft() {
    return priorAuthorityId != null && !submitted && draftContent != null;
  }

  Map<UUID, String> retainedFilenames() {
    Map<UUID, String> filenames = new LinkedHashMap<>();
    documentFilenames.forEach(
        (documentId, filename) -> {
          if (filename != null) {
            filenames.put(documentId, filename);
          }
        });
    return filenames;
  }
}
