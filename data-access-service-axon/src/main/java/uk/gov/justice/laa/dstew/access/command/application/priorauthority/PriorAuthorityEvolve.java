package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
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
    state.draftContent = event.content();
    state.draftModifiedAt = event.occurredAt();
  }

  /** Applies a {@link PriorAuthorityDraftUpdatedEvent}, replacing the draft content. */
  public static void apply(PriorAuthorityState state, PriorAuthorityDraftUpdatedEvent event) {
    state.draftContent = event.content();
    state.draftModifiedAt = event.occurredAt();
  }

  /**
   * Applies a {@link PriorAuthoritySubmittedEvent}: the submitted content replaces the draft,
   * keeping the retained draft document filenames, as version 0 of {@code prior_authority_data}
   * does.
   */
  public static void apply(PriorAuthorityState state, PriorAuthoritySubmittedEvent event) {
    state.priorAuthorityId = event.priorAuthorityId();
    state.applicationId = event.applicationId();
    state.priorAuthorityType = event.priorAuthorityType();
    state.billingType = event.billingType();
    state.submission = submittedContent(state, event);
    state.draftContent = null;
    state.draftModifiedAt = null;
    state.documentFilenames.clear();
    state.schemaVersion = event.schemaVersion();
    state.dataVersion = event.dataVersion();
    state.submitted = true;
    state.decided = false;
  }

  /** Applies a {@link PriorAuthorityDecisionMadeEvent} to the given state. */
  public static void apply(PriorAuthorityState state, PriorAuthorityDecisionMadeEvent event) {
    state.priorAuthorityId = event.priorAuthorityId();
    state.applicationId = event.applicationId();
    state.priorAuthorityType = event.priorAuthorityType();
    state.dataVersion = event.dataVersion();
    state.submitted = true;
    state.decided = true;
    if (state.submission != null) {
      state.submission =
          state.submission.withDecision(
              new PriorAuthorityDataPayload.DecisionDetails(
                  event.overallDecision(),
                  event.decisionJustification(),
                  event.amountGranted(),
                  event.dateGranted(),
                  event.expertFee(),
                  event.disbursementInformation(),
                  event.apportionmentInformation()));
    }
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
            event.size(),
            event.contentType(),
            event.checksum(),
            event.sourceService(),
            false,
            event.fileSuffix()));
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
    state.draftModifiedAt = event.occurredAt();
    state.uploadedDocuments.replaceAll(
        document ->
            document.documentId().equals(event.documentId())
                ? document.withDocumentType(event.documentType())
                : document);
  }

  private static @Nullable PriorAuthorityDataPayload submittedContent(
      PriorAuthorityState state, PriorAuthoritySubmittedEvent event) {
    if (event.content() == null) {
      return null;
    }
    return new PriorAuthorityDataPayload(
        event.priorAuthorityId(),
        event.applicationId(),
        event.content(),
        event.occurredAt(),
        null,
        state.officeCode,
        state.retainedFilenames());
  }
}
