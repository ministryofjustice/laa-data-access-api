package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision.PriorAuthorityDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemUnassigned;

/** Event-fold functions for {@link PriorAuthorityState}. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PriorAuthorityEvolve {

  /** Applies a {@link PriorAuthorityDraftStartedEvent} to the given state. */
  public static void apply(PriorAuthorityState state, PriorAuthorityDraftStartedEvent event) {
    state.priorAuthorityId = event.priorAuthorityId();
    state.applicationId = event.applicationId();
    state.priorAuthorityType = event.priorAuthorityType();
    state.schemaVersion = event.schemaVersion();
    state.submitted = false;
    state.decided = false;
    state.draftOpen = true;
  }

  /** Applies a {@link PriorAuthoritySubmittedEvent} to the given state. */
  public static void apply(PriorAuthorityState state, PriorAuthoritySubmittedEvent event) {
    state.priorAuthorityId = event.priorAuthorityId();
    state.applicationId = event.applicationId();
    state.priorAuthorityType = event.priorAuthorityType();
    state.schemaVersion = event.schemaVersion();
    state.dataVersion = event.dataVersion();
    state.submitted = true;
    state.decided = false;
    state.draftOpen = false;
  }

  /** Applies a {@link PriorAuthorityDecisionMadeEvent} to the given state. */
  public static void apply(PriorAuthorityState state, PriorAuthorityDecisionMadeEvent event) {
    state.priorAuthorityId = event.priorAuthorityId();
    state.applicationId = event.applicationId();
    state.priorAuthorityType = event.priorAuthorityType();
    state.dataVersion = event.dataVersion();
    state.submitted = true;
    state.decided = true;
  }

  /** Applies a generic direct PA assignment. */
  public static void apply(PriorAuthorityState state, WorkItemAssigned event) {
    state.assignmentVersion = event.assignmentVersion();
    state.caseworkerId = event.caseworkerId();
  }

  /** Applies a generic direct PA unassignment. */
  public static void apply(PriorAuthorityState state, WorkItemUnassigned event) {
    state.assignmentVersion = event.assignmentVersion();
    state.caseworkerId = null;
  }

  /** Applies a {@link PriorAuthorityDocumentUploadedEvent} to the given state. */
  public static void apply(PriorAuthorityState state, PriorAuthorityDocumentUploadedEvent event) {
    state.uploadedDocuments.add(
        new UploadedDocumentData(
            event.documentId(),
            event.size(),
            event.fileType(),
            event.contentType(),
            event.sourceService(),
            event.documentType(),
            event.uploadedAt(),
            null));
  }

  /** Applies a {@link PriorAuthorityDocumentDeletedEvent} to the given state. */
  public static void apply(PriorAuthorityState state, PriorAuthorityDocumentDeletedEvent event) {
    state.uploadedDocuments.stream()
        .filter(document -> document.documentId().equals(event.documentId()))
        .findFirst()
        .ifPresent(
            document ->
                state.uploadedDocuments.set(
                    state.uploadedDocuments.indexOf(document),
                    new UploadedDocumentData(
                        document.documentId(),
                        document.size(),
                        document.fileType(),
                        document.contentType(),
                        document.sourceService(),
                        document.documentType(),
                        document.uploadedAt(),
                        event.deletedAt())));
  }

  /** Applies a {@link PriorAuthorityDocumentTypeUpdatedEvent} to the given state. */
  public static void apply(
      PriorAuthorityState state, PriorAuthorityDocumentTypeUpdatedEvent event) {
    state.uploadedDocuments.stream()
        .filter(document -> document.documentId().equals(event.documentId()))
        .findFirst()
        .ifPresent(
            document ->
                state.uploadedDocuments.set(
                    state.uploadedDocuments.indexOf(document),
                    document.withDocumentType(event.documentType())));
  }
}
