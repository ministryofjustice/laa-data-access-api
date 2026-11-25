package uk.gov.justice.laa.dstew.access.command.application;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
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
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataPayload;
import uk.gov.justice.laa.dstew.access.document.DocumentMetadata;
import uk.gov.justice.laa.dstew.access.model.PotentialDuplicate;

/** State object reconstructed by folding an aggregate's event stream. */
@Getter
@NoArgsConstructor
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ApplicationState {
  UUID applicationId;
  int schemaVersion;
  String requestFingerprint;
  boolean submissionRecorded;
  String status;
  String officeCode;
  AutoGrantedState autoGranted = AutoGrantedState.PENDING;
  String overallDecision;
  long applicationDataVersion;
  long applicationVersion;
  UUID caseworkerId;
  long assignmentVersion;
  List<DocumentMetadata> uploadedDocuments = new ArrayList<>();
  String laaReference;
  // Draft content, held only until submission.
  String draftStatus;

  @Getter(AccessLevel.NONE)
  @Nullable Map<String, Object> draftApplicationContent;

  @Getter(AccessLevel.NONE)
  @Nullable List<PotentialDuplicate> draftPotentialDuplicates;

  // A null value means the filename has been erased; the key is kept.
  Map<UUID, @Nullable String> draftDocumentFilenames = new LinkedHashMap<>();
  // Kept linking keys and decision fingerprint, available after content is erased.
  List<UUID> proceedingIds = new ArrayList<>();
  String decisionRequestFingerprint;
  // Kept decision value per proceeding, merged across decisions; informs retention schedules.
  Map<UUID, String> proceedingDecisions = new LinkedHashMap<>();

  // Current submitted content, folded from event content; null if any part of it is erased.
  @Getter(AccessLevel.NONE)
  @Nullable ApplicationDataPayload content;

  /** The open draft's application content; empty if there is none or it has been erased. */
  public Optional<Map<String, Object>> draftApplicationContent() {
    return Optional.ofNullable(draftApplicationContent);
  }

  /** The open draft's potential duplicates; empty if there are none or they have been erased. */
  public Optional<List<PotentialDuplicate>> draftPotentialDuplicates() {
    return Optional.ofNullable(draftPotentialDuplicates);
  }

  /** The current submitted content; empty before submission or if any part has been erased. */
  public Optional<ApplicationDataPayload> content() {
    return Optional.ofNullable(content);
  }
}
