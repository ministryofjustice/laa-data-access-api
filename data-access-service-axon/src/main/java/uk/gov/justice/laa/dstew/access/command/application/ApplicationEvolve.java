package uk.gov.justice.laa.dstew.access.command.application;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.applicationcontent.DecisionValue;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.draft.ApplicationDraftStartedEvent;
import uk.gov.justice.laa.dstew.access.command.application.note.NoteCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.ready.ApplicationReadyForManualAssessmentEvent;
import uk.gov.justice.laa.dstew.access.command.application.update.ApplicationUpdatedEvent;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemUnassigned;
import uk.gov.justice.laa.dstew.access.document.DocumentMetadata;
import uk.gov.justice.laa.dstew.access.eventcontent.Erasable;

/** Event-fold functions for {@link ApplicationState}. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ApplicationEvolve {

  /** Applies an {@link ApplicationCreatedEvent} to the given state. */
  public static void apply(ApplicationState state, ApplicationCreatedEvent event) {
    state.applicationId = event.applicationId();
    state.schemaVersion = event.schemaVersion();
    state.requestFingerprint = event.requestFingerprint();
    state.status = event.status();
    state.officeCode = event.officeCode();
    state.autoGranted = AutoGrantedState.PENDING;
    state.applicationDataVersion = event.applicationDataVersion();
    state.applicationVersion = 0L;
    state.laaReference = event.laaReference();
    state.draftStatus = null;
    state.draftApplicationContent = Erasable.erased();
    state.draftSerialisedRequest = Erasable.erased();
    state.draftPotentialDuplicates = Erasable.erased();
    state.draftDocumentFilenames.clear();
  }

  /** Applies an {@link ApplicationDraftStartedEvent} to the given state. */
  public static void apply(ApplicationState state, ApplicationDraftStartedEvent event) {
    state.applicationId = event.applicationId();
    state.schemaVersion = event.schemaVersion();
    state.requestFingerprint = event.requestFingerprint();
    state.officeCode = event.officeCode();
    state.laaReference = event.laaReference();
    state.draftStatus = event.status();
    state.draftApplicationContent = event.applicationContent();
    state.draftSerialisedRequest = event.serialisedRequest();
    state.draftPotentialDuplicates = event.potentialDuplicates();
  }

  /** Applies an {@link ApplicationUpdatedEvent} to the given state. */
  public static void apply(ApplicationState state, ApplicationUpdatedEvent event) {
    state.status = event.status();
    state.applicationVersion = event.applicationVersion();
    state.applicationDataVersion = event.applicationDataVersion();
    state.autoGranted = event.enteredSubmitted() ? AutoGrantedState.PENDING : state.autoGranted;
  }

  /** Applies an {@link ApplicationDecisionMadeEvent} to the given state. */
  public static void apply(ApplicationState state, ApplicationDecisionMadeEvent event) {
    if (event.overallDecision() != null) {
      state.status =
          DecisionValue.valueOf(event.overallDecision()).equals(DecisionValue.REFUSED)
              ? ApplicationStatus.APPLICATION_REFUSED.getValue()
              : ApplicationStatus.APPLICATION_GRANTED.getValue();
    }
    state.applicationVersion = event.applicationVersion();
    state.applicationDataVersion = event.applicationDataVersion();
    state.autoGranted = event.autoGranted();
    state.overallDecision = event.overallDecision();
  }

  /** Applies an {@link ApplicationReadyForManualAssessmentEvent} to the given state. */
  public static void apply(ApplicationState state, ApplicationReadyForManualAssessmentEvent event) {
    state.applicationVersion = event.applicationVersion();
    state.applicationDataVersion = event.applicationDataVersion();
    state.autoGranted = AutoGrantedState.MANUAL;
  }

  /** Applies a generic direct assignment event to the owning application state. */
  public static void apply(ApplicationState state, WorkItemAssigned event) {
    state.assignmentVersion = event.assignmentVersion();
    state.caseworkerId = event.caseworkerId();
  }

  /** Applies a generic direct unassignment event to the owning application state. */
  public static void apply(ApplicationState state, WorkItemUnassigned event) {
    state.assignmentVersion = event.assignmentVersion();
    state.caseworkerId = null;
  }

  /** Applies a {@link NoteCreatedEvent} to the given state. */
  public static void apply(ApplicationState state, NoteCreatedEvent event) {
    state.applicationDataVersion = event.applicationDataVersion();
    // applicationVersion intentionally not updated — notes are decoupled from optimistic locking
  }

  /** Applies an {@link ApplicationDocumentUploadedEvent} to the given state. */
  public static void apply(ApplicationState state, ApplicationDocumentUploadedEvent event) {
    state.uploadedDocuments.add(
        new DocumentMetadata(
            event.documentId(),
            event.documentType().orElse(null),
            event.uploadedAt(),
            event.size().orElse(null),
            event.contentType().orElse(null),
            event.checksum(),
            event.sourceService().orElse(null),
            false,
            event.fileSuffix().orElse(null)));
    state.draftDocumentFilenames.put(event.documentId(), event.originalFilename());
    if (event.applicationDataVersion() != null) {
      state.applicationDataVersion = event.applicationDataVersion();
    }
  }
}
