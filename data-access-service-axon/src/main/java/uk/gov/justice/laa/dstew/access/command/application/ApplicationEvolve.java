package uk.gov.justice.laa.dstew.access.command.application;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import uk.gov.justice.laa.dstew.access.command.application.assignment.ApplicationAssignedToCaseworkerEvent;
import uk.gov.justice.laa.dstew.access.command.application.assignment.ApplicationUnassignedFromCaseworkerEvent;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupRequested;
import uk.gov.justice.laa.dstew.access.command.application.note.NoteCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.ready.ApplicationReadyForManualAssessmentEvent;

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
    state.autoGranted = null;
    state.applicationDataVersion = event.applicationDataVersion();
    state.applicationVersion = 0L;
  }

  /** Applies an {@link ApplicationDecisionMadeEvent} to the given state. */
  public static void apply(ApplicationState state, ApplicationDecisionMadeEvent event) {
    state.applicationVersion = event.applicationVersion();
    state.applicationDataVersion = event.applicationDataVersion();
    state.autoGranted = event.autoGranted();
  }

  /** Applies an {@link ApplicationReadyForManualAssessmentEvent} to the given state. */
  public static void apply(ApplicationState state, ApplicationReadyForManualAssessmentEvent event) {
    state.applicationVersion = event.applicationVersion();
    state.applicationDataVersion = event.applicationDataVersion();
    state.autoGranted = false;
  }

  /** Applies an {@link ApplicationAssignedToCaseworkerEvent} to the given state. */
  public static void apply(ApplicationState state, ApplicationAssignedToCaseworkerEvent event) {
    state.applicationVersion = event.applicationVersion();
    state.applicationDataVersion = event.applicationDataVersion();
    state.caseworkerId = event.caseworkerId();
  }

  /** Applies an {@link ApplicationUnassignedFromCaseworkerEvent} to the given state. */
  public static void apply(ApplicationState state, ApplicationUnassignedFromCaseworkerEvent event) {
    state.applicationVersion = event.applicationVersion();
    state.applicationDataVersion = event.applicationDataVersion();
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
