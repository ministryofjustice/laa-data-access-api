package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision.PriorAuthorityDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemUnassigned;
import uk.gov.justice.laa.dstew.access.document.DocumentMetadata;

/** Event-fold functions for {@link PriorAuthorityState}. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PriorAuthorityEvolve {

  /** Applies a {@link PriorAuthorityDraftStartedEvent} to the given state. */
  public static void apply(PriorAuthorityState state, PriorAuthorityDraftStartedEvent event) {
    state.priorAuthorityId = event.priorAuthorityId();
    state.applicationId = event.applicationId();
    state.officeCode = event.officeCode();
    state.priorAuthorityType = event.priorAuthorityType();
    state.schemaVersion = event.schemaVersion();
    state.submitted = false;
    state.decided = false;
    state.content = event.content();
    state.serialisedRequest = event.serialisedRequest();
    state.draftModifiedAt = event.occurredAt();
  }

  /** Applies a {@link PriorAuthorityDraftUpdatedEvent}, replacing the draft content. */
  public static void apply(PriorAuthorityState state, PriorAuthorityDraftUpdatedEvent event) {
    state.content = event.content();
    state.serialisedRequest = event.serialisedRequest();
    state.draftModifiedAt = event.occurredAt();
  }

  /**
   * Applies a {@link PriorAuthoritySubmittedEvent}, sealing the draft content as the submission at
   * version 0.
   */
  public static void apply(PriorAuthorityState state, PriorAuthoritySubmittedEvent event) {
    state.priorAuthorityId = event.priorAuthorityId();
    state.applicationId = event.applicationId();
    state.priorAuthorityType = event.priorAuthorityType();
    state.schemaVersion = event.schemaVersion();
    state.priorAuthorityVersion = 0L;
    state.submittedAt = event.occurredAt();
    state.submitted = true;
    state.decided = false;
  }

  /** Applies a {@link PriorAuthorityDecisionMadeEvent} to the given state. */
  public static void apply(PriorAuthorityState state, PriorAuthorityDecisionMadeEvent event) {
    state.priorAuthorityId = event.priorAuthorityId();
    state.applicationId = event.applicationId();
    state.priorAuthorityType = event.priorAuthorityType();
    state.priorAuthorityVersion++;
    state.overallDecision = event.overallDecision();
    state.decisionJustification = event.decisionJustification();
    state.amountGranted = event.amountGranted();
    state.dateGranted = event.dateGranted();
    state.expertFee = event.expertFee();
    state.disbursementInformation = event.disbursementInformation();
    state.apportionmentInformation = event.apportionmentInformation();
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
        new DocumentMetadata(
            event.documentId(),
            null,
            event.uploadedAt(),
            event.size().orElse(null),
            event.contentType().orElse(null),
            event.checksum(),
            event.sourceService().orElse(null),
            false,
            event.fileSuffix().orElse(null)));
    state.documentFilenames.put(event.documentId(), event.originalFilename());
  }

  /** Applies a {@link PriorAuthorityDocumentDeletedEvent} to the given state. */
  public static void apply(PriorAuthorityState state, PriorAuthorityDocumentDeletedEvent event) {
    state.uploadedDocuments.replaceAll(
        document ->
            document.documentId().equals(event.documentId())
                ? document.withDeleted(true)
                : document);
    state.documentFilenames.remove(event.documentId());
  }

  /** Applies a {@link PriorAuthorityDocumentTypeUpdatedEvent} to the given state. */
  public static void apply(
      PriorAuthorityState state, PriorAuthorityDocumentTypeUpdatedEvent event) {
    state.uploadedDocuments.replaceAll(
        document ->
            document.documentId().equals(event.documentId())
                ? document.withDocumentType(event.documentType().orElse(null))
                : document);
  }
}
