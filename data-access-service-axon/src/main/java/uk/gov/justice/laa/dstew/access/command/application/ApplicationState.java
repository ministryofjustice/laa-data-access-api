package uk.gov.justice.laa.dstew.access.command.application;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataPayload;
import uk.gov.justice.laa.dstew.access.document.DocumentMetadata;
import uk.gov.justice.laa.dstew.access.eventcontent.Erasable;
import uk.gov.justice.laa.dstew.access.model.PotentialDuplicate;

/** State object reconstructed by folding an aggregate's event stream. */
@Getter
@NoArgsConstructor
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ApplicationState {
  UUID applicationId;
  int schemaVersion;
  String requestFingerprint;
  String status;
  String officeCode;
  AutoGrantedState autoGranted = AutoGrantedState.PENDING;
  String overallDecision;
  long applicationVersion;
  UUID caseworkerId;
  long assignmentVersion;
  List<DocumentMetadata> uploadedDocuments = new ArrayList<>();
  String laaReference;
  // Draft content, held only until submission.
  String draftStatus;
  Erasable<Map<String, Object>> draftApplicationContent = Erasable.erased();
  Erasable<String> draftSerialisedRequest = Erasable.erased();
  Erasable<List<PotentialDuplicate>> draftPotentialDuplicates = Erasable.erased();
  Map<UUID, Erasable<String>> draftDocumentFilenames = new LinkedHashMap<>();
  // Kept linking keys and decision fingerprint, available after content is erased.
  List<UUID> proceedingIds = new ArrayList<>();
  String decisionRequestFingerprint;
  // Kept decision value per proceeding, merged across decisions; informs retention schedules.
  Map<UUID, String> proceedingDecisions = new LinkedHashMap<>();
  // Current submitted content, folded from event content; erased if any part of it is erased.
  Erasable<ApplicationDataPayload> content = Erasable.erased();
  // Potential duplicates recorded at submission.
  Erasable<List<PotentialDuplicate>> potentialDuplicates = Erasable.erased();
  // Kept lifecycle timestamps, taken from the events' own times.
  Instant createdAt;
  Instant submittedAt;
  Instant modifiedAt;
}
