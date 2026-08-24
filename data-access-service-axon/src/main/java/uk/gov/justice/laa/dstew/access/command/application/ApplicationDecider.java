package uk.gov.justice.laa.dstew.access.command.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import uk.gov.justice.laa.dstew.access.applicationcontent.DecisionValue;
import uk.gov.justice.laa.dstew.access.applicationcontent.LinkedApplication;
import uk.gov.justice.laa.dstew.access.applicationcontent.Proceeding;
import uk.gov.justice.laa.dstew.access.command.application.assignment.ApplicationAssignedToCaseworkerEvent;
import uk.gov.justice.laa.dstew.access.command.application.assignment.ApplicationUnassignedFromCaseworkerEvent;
import uk.gov.justice.laa.dstew.access.command.application.assignment.AssignCaseworkerToApplicationCommand;
import uk.gov.justice.laa.dstew.access.command.application.assignment.UnassignCaseworkerFromApplicationCommand;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.decision.MakeApplicationDecisionCommand;
import uk.gov.justice.laa.dstew.access.command.application.decision.MakeDecisionProceeding;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupRequested;
import uk.gov.justice.laa.dstew.access.command.application.note.CreateNoteCommand;
import uk.gov.justice.laa.dstew.access.command.application.note.NoteCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.ready.MarkApplicationReadyCommand;
import uk.gov.justice.laa.dstew.access.command.application.ready.ReadyApplicationResult;
import uk.gov.justice.laa.dstew.access.command.application.update.ApplicationUpdatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.update.UpdateApplicationCommand;
import uk.gov.justice.laa.dstew.access.exception.ApplicationAutoGrantOutcomeConflictException;
import uk.gov.justice.laa.dstew.access.exception.ApplicationCreationConflictException;
import uk.gov.justice.laa.dstew.access.exception.ApplicationGroupInvariantException;
import uk.gov.justice.laa.dstew.access.exception.ApplicationVersionConflictException;
import uk.gov.justice.laa.dstew.access.exception.InvalidApplicationStateException;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

/** Decision functions: derive events from current state and command inputs. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ApplicationDecider {

  /**
   * Returns a singleton {@link ApplicationCreatedEvent} for a new application, an empty list for an
   * idempotent retry with the same fingerprint and schema version, or throws {@link
   * ApplicationCreationConflictException} on a conflicting retry.
   *
   * @param details {@code null} when the aggregate already exists
   */
  public static List<Object> decideCreate(
      ApplicationState state,
      UUID applicationId,
      int schemaVersion,
      String fingerprint,
      ApplicationCreationDetails details,
      long applicationDataVersion) {

    if (state.applicationId != null) {
      if (state.requestFingerprint.equals(fingerprint) && state.schemaVersion == schemaVersion) {
        return Collections.emptyList();
      }
      throw new ApplicationCreationConflictException(state.applicationId);
    }

    if (details.leadApplicationId() != null && details.leadApplicationId().equals(applicationId)) {
      throw new ApplicationGroupInvariantException(
          "Application " + applicationId + " cannot be its own lead");
    }

    return List.of(
        buildApplicationCreatedEvent(applicationId, applicationDataVersion, fingerprint, details));
  }

  /** Returns a singleton {@link LinkedApplicationGroupRequested} or throws if already a member. */
  public static LinkedApplicationGroupRequested decideCreateLinkedGroup(
      ApplicationState state, UUID groupId, List<UUID> allMemberIds, Instant occurredAt) {

    if (state.isAssociatedMember) {
      throw new ApplicationGroupInvariantException(
          "Application "
              + state.applicationId
              + " is already a member of another group and cannot be a lead");
    }
    return new LinkedApplicationGroupRequested(
        groupId, state.applicationId, allMemberIds, occurredAt);
  }

  /**
   * Validates the command and returns an {@link ApplicationDecisionMadeEvent}.
   *
   * @param current the current application-data payload
   */
  public static ApplicationDecisionMadeEvent decideDecision(
      ApplicationState state,
      MakeApplicationDecisionCommand command,
      ApplicationDataPayload current,
      AutoGrantedState autoGranted) {

    if (command.expectedApplicationVersion() != state.applicationVersion) {
      throw new ApplicationVersionConflictException(
          command.applicationId(), command.expectedApplicationVersion());
    }
    validateDecision(command);

    Set<UUID> linkedProceedingIds =
        current.proceedings().stream().map(Proceeding::getId).collect(Collectors.toSet());
    List<UUID> unknownProceedingIds =
        command.proceedings().stream()
            .map(MakeDecisionProceeding::proceedingId)
            .distinct()
            .filter(id -> !linkedProceedingIds.contains(id))
            .toList();
    if (!unknownProceedingIds.isEmpty()) {
      throw new ResourceNotFoundException(
          "No proceeding found with id: "
              + unknownProceedingIds.stream().map(UUID::toString).collect(Collectors.joining(",")));
    }
    return new ApplicationDecisionMadeEvent(
        state.applicationId,
        state.applicationVersion + 1,
        state.applicationDataVersion + 1,
        command.overallDecision(),
        autoGranted,
        command.occurredAt());
  }

  /** Validates an ordinary decision, which always records manual assessment. */
  public static ApplicationDecisionMadeEvent decideDecision(
      ApplicationState state,
      MakeApplicationDecisionCommand command,
      ApplicationDataPayload current) {
    return decideDecision(state, command, current, AutoGrantedState.MANUAL);
  }

  /** Returns an {@link ApplicationAssignedToCaseworkerEvent}. */
  public static ApplicationAssignedToCaseworkerEvent decideAssign(
      ApplicationState state, AssignCaseworkerToApplicationCommand command) {
    return new ApplicationAssignedToCaseworkerEvent(
        state.applicationId,
        state.applicationVersion + 1,
        state.applicationDataVersion + 1,
        command.caseworkerId(),
        command.occurredAt());
  }

  /**
   * Returns an {@link ApplicationUnassignedFromCaseworkerEvent}, or throws if no caseworker is
   * assigned.
   */
  public static ApplicationUnassignedFromCaseworkerEvent decideUnassign(
      ApplicationState state, UnassignCaseworkerFromApplicationCommand command) {
    if (state.caseworkerId == null) {
      throw new ValidationException(
          List.of("The request cannot be completed: no caseworker is assigned"));
    }
    return new ApplicationUnassignedFromCaseworkerEvent(
        state.applicationId,
        state.applicationVersion + 1,
        state.applicationDataVersion + 1,
        command.occurredAt());
  }

  /** Returns a {@link NoteCreatedEvent}. */
  public static NoteCreatedEvent decideNote(ApplicationState state, CreateNoteCommand command) {
    return new NoteCreatedEvent(
        state.applicationId, state.applicationDataVersion + 1, command.occurredAt());
  }

  /** Validates an idempotent transition to manual-assessment readiness. */
  public static ReadyApplicationResult decideReady(
      ApplicationState state, MarkApplicationReadyCommand command) {
    if (state.autoGranted == AutoGrantedState.MANUAL) {
      return ReadyApplicationResult.ALREADY_RECORDED;
    }
    if (state.autoGranted == AutoGrantedState.AUTOGRANTED) {
      throw new ApplicationAutoGrantOutcomeConflictException(command.applicationId());
    }
    if (!"APPLICATION_SUBMITTED".equals(state.status)) {
      throw new InvalidApplicationStateException(command.applicationId(), state.status);
    }
    if (command.expectedApplicationVersion() != null
        && command.expectedApplicationVersion() != state.applicationVersion) {
      throw new ApplicationVersionConflictException(
          command.applicationId(), command.expectedApplicationVersion());
    }
    return ReadyApplicationResult.RECORDED;
  }

  /** Returns the next replayable state transition for an Application update. */
  public static ApplicationUpdatedEvent decideUpdate(
      ApplicationState state, UpdateApplicationCommand command) {
    String nextStatus = command.status() == null ? state.status : command.status();
    return new ApplicationUpdatedEvent(
        state.applicationId,
        state.applicationVersion + 1,
        state.applicationDataVersion + 1,
        state.status,
        nextStatus,
        command.occurredAt());
  }

  /** Validates that the application holds an overall decision of {@code GRANTED}. */
  public static void validateGranted(ApplicationState state) {
    if (!"GRANTED".equals(state.overallDecision)) {
      throw new ValidationException(
          List.of(
              "Prior authority requires the application to have an overall decision of GRANTED"));
    }
  }

  private static void validateDecision(MakeApplicationDecisionCommand command) {
    List<String> errors = new ArrayList<>();
    if (command.proceedings().isEmpty()) {
      errors.add("The request must contain at least one proceeding");
    }
    if (DecisionValue.GRANTED.name().equals(command.overallDecision())
        && (command.certificate() == null || command.certificate().isEmpty())) {
      errors.add("The request must contain a certificate when overallDecision is GRANTED");
    }
    command.proceedings().stream()
        .filter(p -> p.justification() == null || p.justification().isEmpty())
        .forEach(
            p ->
                errors.add(
                    "The request must contain a refusal justification for proceeding: "
                        + p.proceedingId()));
    Set<UUID> ids = new HashSet<>();
    command.proceedings().stream()
        .map(MakeDecisionProceeding::proceedingId)
        .filter(id -> !ids.add(id))
        .forEach(id -> errors.add("Duplicate proceeding id: " + id));
    if (!errors.isEmpty()) {
      throw new ValidationException(errors);
    }
  }

  private static ApplicationCreatedEvent buildApplicationCreatedEvent(
      UUID applicationId,
      long applicationDataVersion,
      String fingerprint,
      ApplicationCreationDetails details) {
    return new ApplicationCreatedEvent(
        applicationId,
        applicationDataVersion,
        fingerprint,
        details.status(),
        details.schemaVersion(),
        details.occurredAt(),
        details.leadApplicationId(),
        details.allLinkedApplications() == null
            ? List.of()
            : details.allLinkedApplications().stream()
                .map(LinkedApplication::getAssociatedApplicationId)
                .filter(Objects::nonNull)
                .distinct()
                .toList());
  }
}
