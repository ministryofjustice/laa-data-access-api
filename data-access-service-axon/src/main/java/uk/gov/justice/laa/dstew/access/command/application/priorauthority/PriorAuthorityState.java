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
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityStatus;
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
  List<DocumentMetadata> uploadedDocuments = new ArrayList<>();
  Erasable<PriorAuthorityContent> draftContent = Erasable.erased();
  Erasable<String> draftSerialisedRequest = Erasable.erased();
  Instant draftModifiedAt;
  Map<UUID, Erasable<String>> documentFilenames = new LinkedHashMap<>();

  /** Returns the processing status derived from the submitted and decided flags. */
  public PriorAuthorityStatus status() {
    if (decided) {
      return PriorAuthorityStatus.DECIDED;
    }
    return submitted ? PriorAuthorityStatus.SUBMITTED : PriorAuthorityStatus.DRAFT;
  }

  /** Returns the open draft's content, or empty once submitted or when the content is erased. */
  public Optional<PriorAuthorityDataPayload> draft() {
    if (priorAuthorityId == null || submitted || draftContent.isErased()) {
      return Optional.empty();
    }
    return Optional.of(
        new PriorAuthorityDataPayload(
            priorAuthorityId,
            applicationId,
            draftContent.value(),
            draftSerialisedRequest.orElse(null),
            draftModifiedAt,
            null,
            officeCode,
            retainedFilenames()));
  }

  /** Returns the recorded original filename of each live document whose filename is retained. */
  public Map<UUID, String> retainedFilenames() {
    Map<UUID, String> filenames = new LinkedHashMap<>();
    documentFilenames.forEach(
        (documentId, filename) -> {
          if (filename.orElse(null) != null) {
            filenames.put(documentId, filename.value());
          }
        });
    return filenames;
  }
}
