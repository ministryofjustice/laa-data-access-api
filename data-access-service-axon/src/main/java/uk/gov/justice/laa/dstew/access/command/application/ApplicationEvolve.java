package uk.gov.justice.laa.dstew.access.command.application;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.applicationcontent.DecisionValue;
import uk.gov.justice.laa.dstew.access.command.application.assignment.ApplicationAssignedToCaseworkerEvent;
import uk.gov.justice.laa.dstew.access.command.application.assignment.ApplicationUnassignedFromCaseworkerEvent;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupRequested;
import uk.gov.justice.laa.dstew.access.command.application.note.NoteCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.ready.ApplicationReadyForManualAssessmentEvent;
import uk.gov.justice.laa.dstew.access.command.application.update.ApplicationUpdatedEvent;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemUnassigned;

/** Event-fold functions for {@link ApplicationState}. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ApplicationEvolve {

  /** Applies an {@link ApplicationCreatedEvent} to the given state. */
  public static void apply(ApplicationState state, ApplicationCreatedEvent event) {
    state.applicationId = event.applicationId();
    state.isAssociatedMember = event.leadApplicationId() != null;
    state.schemaVersion = event.schemaVersion();
    state.requestFingerprint = event.requestFingerprint();
    state.status = event.status();
    state.autoGranted = AutoGrantedState.PENDING;
    state.applicationDataVersion = event.applicationDataVersion();
    state.applicationVersion = 0L;
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

  /** Applies an {@link ApplicationAssignedToCaseworkerEvent} to the given state. */
  public static void apply(ApplicationState state, ApplicationAssignedToCaseworkerEvent event) {
    state.applicationVersion = event.applicationVersion();
    state.applicationDataVersion = event.applicationDataVersion();
    state.caseworkerId = event.caseworkerId();
    state.assignmentVersion++;
  }

  /** Applies an {@link ApplicationUnassignedFromCaseworkerEvent} to the given state. */
  public static void apply(ApplicationState state, ApplicationUnassignedFromCaseworkerEvent event) {
    state.applicationVersion = event.applicationVersion();
    state.applicationDataVersion = event.applicationDataVersion();
    state.caseworkerId = null;
    state.assignmentVersion++;
  }

  /** Applies a generic direct assignment event to the owning application state. */
  public static void apply(ApplicationState state, WorkItemAssigned event) {
    state.applicationVersion = event.itemVersion();
    state.applicationDataVersion = event.dataVersion();
    state.assignmentVersion = event.assignmentVersion();
    state.caseworkerId = event.caseworkerId();
  }

  /** Applies a generic direct unassignment event to the owning application state. */
  public static void apply(ApplicationState state, WorkItemUnassigned event) {
    state.applicationVersion = event.itemVersion();
    state.applicationDataVersion = event.dataVersion();
    state.assignmentVersion = event.assignmentVersion();
    state.caseworkerId = null;
  }

  /** Applies a {@link NoteCreatedEvent} to the given state. */
  public static void apply(ApplicationState state, NoteCreatedEvent event) {
    state.applicationDataVersion = event.applicationDataVersion();
    // applicationVersion intentionally not updated — notes are decoupled from optimistic locking
  }

  /** Applies an {@link ApplicationLinkedEvent} to the given state. */
  public static void apply(ApplicationState state, ApplicationLinkedEvent event) {
    state.isAssociatedMember = true;
  }

  /** Applies a {@link LinkedApplicationGroupRequested} to the given state. */
  public static void apply(ApplicationState state, LinkedApplicationGroupRequested event) {}
}
