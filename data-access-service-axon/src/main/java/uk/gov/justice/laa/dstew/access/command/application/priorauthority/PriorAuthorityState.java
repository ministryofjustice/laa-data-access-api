package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent;
import uk.gov.justice.laa.dstew.access.document.DocumentMetadata;
import uk.gov.justice.laa.dstew.access.eventcontent.Erasable;

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
  Erasable<PriorAuthorityContent> draftContent = Erasable.erased();
  Erasable<String> draftSerialisedRequest = Erasable.erased();
  Instant draftModifiedAt;
  Map<UUID, Erasable<String>> documentFilenames = new LinkedHashMap<>();
  Erasable<PriorAuthorityDataPayload> submission = Erasable.erased();

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
            draftContent.value(),
            draftSerialisedRequest.value(),
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
    if (priorAuthorityId == null || !submitted || submission.isErased()) {
      return Optional.empty();
    }
    return Optional.of(submission.value());
  }

  /** Command pre-check: return no draft body, and only the filename requested for SDS cleanup. */
  public PriorAuthorityDraftSummary draftSummary(UUID documentId) {
    if (!hasOpenDraft()) {
      return PriorAuthorityDraftSummary.NONE;
    }
    var filename = documentFilenames.get(documentId);
    return new PriorAuthorityDraftSummary(
        applicationId, filename == null ? null : filename.orElse(null));
  }

  private boolean hasOpenDraft() {
    return priorAuthorityId != null
        && !submitted
        && !draftContent.isErased()
        && !draftSerialisedRequest.isErased();
  }

  Map<UUID, String> retainedFilenames() {
    Map<UUID, String> filenames = new LinkedHashMap<>();
    documentFilenames.forEach(
        (documentId, filename) -> {
          if (!filename.isErased()) {
            filenames.put(documentId, filename.value());
          }
        });
    return filenames;
  }
}
