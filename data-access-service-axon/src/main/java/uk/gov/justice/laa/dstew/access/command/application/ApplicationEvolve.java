package uk.gov.justice.laa.dstew.access.command.application;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.applicationcontent.DecisionValue;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationMeritsDecision;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.draft.ApplicationDraftStartedEvent;
import uk.gov.justice.laa.dstew.access.command.application.note.NoteCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.ready.ApplicationReadyForManualAssessmentEvent;
import uk.gov.justice.laa.dstew.access.command.application.update.ApplicationUpdatedEvent;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemUnassigned;
import uk.gov.justice.laa.dstew.access.document.DocumentMetadata;

/** Event-fold functions for {@link ApplicationState}. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ApplicationEvolve {

  /** Applies an {@link ApplicationCreatedEvent} to the given state. */
  public static void apply(ApplicationState state, ApplicationCreatedEvent event) {
    state.submissionRecorded = true;
    state.applicationId = event.applicationId();
    state.schemaVersion = event.schemaVersion();
    state.requestFingerprint = event.requestFingerprint();
    state.status = event.status();
    state.officeCode = event.officeCode();
    state.autoGranted = AutoGrantedState.PENDING;
    state.applicationDataVersion = event.applicationDataVersion();
    state.applicationVersion = 0L;
    state.laaReference = event.laaReference();
    state.proceedingIds.clear();
    state.proceedingIds.addAll(event.proceedingIds());
    state.decisionRequestFingerprint = null;
    state.proceedingDecisions.clear();
    state.content = createdContent(state, event);
    state.draftStatus = null;
    state.draftApplicationContent = null;
    state.draftPotentialDuplicates = null;
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
    state.draftPotentialDuplicates = event.potentialDuplicates();
  }

  /** Applies an {@link ApplicationUpdatedEvent} to the given state. */
  public static void apply(ApplicationState state, ApplicationUpdatedEvent event) {
    state.status = event.status();
    state.applicationVersion = event.applicationVersion();
    state.applicationDataVersion = event.applicationDataVersion();
    state.autoGranted = event.enteredSubmitted() ? AutoGrantedState.PENDING : state.autoGranted;
    state.proceedingIds.clear();
    state.proceedingIds.addAll(event.proceedingIds());
    if (event.enteredSubmitted()) {
      state.decisionRequestFingerprint = null;
      state.proceedingDecisions.clear();
    }
    var update = event.content();
    state.content =
        state.content == null || update == null
            ? null
            : state.content.withApplicationUpdate(
                update.client(),
                update.provider(),
                update.opponents(),
                update.submittedAt(),
                update.usedDelegatedFunctions(),
                update.categoryOfLaw(),
                update.matterType(),
                update.categoryOfLawCode(),
                update.matterTypeCode(),
                update.proceedings(),
                event.enteredSubmitted());
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
    state.decisionRequestFingerprint = event.decisionRequestFingerprint();
    state.proceedingDecisions.putAll(event.proceedingDecisions());
    if (state.content != null) {
      state.content =
          state.content.withDecision(
              event.overallDecision(),
              event.autoGranted(),
              mergedMeritsDecisions(state.content, event),
              event.certificate(),
              event.eventDescription());
    }
  }

  /** Applies an {@link ApplicationReadyForManualAssessmentEvent} to the given state. */
  public static void apply(ApplicationState state, ApplicationReadyForManualAssessmentEvent event) {
    state.applicationVersion = event.applicationVersion();
    state.applicationDataVersion = event.applicationDataVersion();
    state.autoGranted = AutoGrantedState.MANUAL;
    if (state.content != null) {
      state.content = state.content.withManualAssessmentRequired();
    }
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
    state.content =
        state.content == null || event.noteText() == null
            ? null
            : state.content.withNote(event.noteText(), event.occurredAt());
  }

  /** Applies an {@link ApplicationDocumentUploadedEvent} to the given state. */
  public static void apply(ApplicationState state, ApplicationDocumentUploadedEvent event) {
    state.uploadedDocuments.add(
        new DocumentMetadata(
            event.documentId(),
            event.documentType(),
            event.uploadedAt(),
            event.size(),
            event.contentType(),
            event.checksum(),
            event.sourceService(),
            false,
            event.fileSuffix()));
    state.draftDocumentFilenames.put(event.documentId(), event.originalFilename());
    if (event.applicationDataVersion() != null) {
      state.applicationDataVersion = event.applicationDataVersion();
    }
  }

  private static @Nullable ApplicationDataPayload createdContent(
      ApplicationState state, ApplicationCreatedEvent event) {
    if (event.content() == null) {
      return null;
    }
    ApplicationDataPayload payload =
        ApplicationDataPayload.from(event.laaReference(), event.content());
    for (var filename : state.draftDocumentFilenames.entrySet()) {
      if (filename.getValue() != null) {
        payload = payload.withDocumentFilename(filename.getKey(), filename.getValue());
      }
    }
    return payload;
  }

  private static Map<UUID, ApplicationMeritsDecision> mergedMeritsDecisions(
      ApplicationDataPayload current, ApplicationDecisionMadeEvent event) {
    Map<UUID, ApplicationMeritsDecision> merged =
        new HashMap<>(Objects.requireNonNullElse(current.meritsDecisions(), Map.of()));
    merged.putAll(Objects.requireNonNullElse(event.meritsDecisions(), Map.of()));
    return merged;
  }
}
