package uk.gov.justice.laa.dstew.access.command.application;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator;
import org.axonframework.extension.spring.stereotype.EventSourced;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import uk.gov.justice.laa.dstew.access.command.application.assignment.ApplicationAssignedToCaseworkerEvent;
import uk.gov.justice.laa.dstew.access.command.application.assignment.ApplicationUnassignedFromCaseworkerEvent;
import uk.gov.justice.laa.dstew.access.command.application.assignment.AssignCaseworkerToApplicationCommand;
import uk.gov.justice.laa.dstew.access.command.application.assignment.UnassignCaseworkerFromApplicationCommand;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationMeritsDecision;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.decision.MakeApplicationDecisionCommand;
import uk.gov.justice.laa.dstew.access.command.application.decision.MakeDecisionProceeding;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.CreateLinkedApplicationGroupCommand;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupRequested;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.ValidateApplicationExistsCommand;
import uk.gov.justice.laa.dstew.access.command.application.note.CreateNoteCommand;
import uk.gov.justice.laa.dstew.access.command.application.note.NoteCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.ready.ApplicationReadyForManualAssessmentEvent;
import uk.gov.justice.laa.dstew.access.command.application.ready.MarkApplicationReadyCommand;
import uk.gov.justice.laa.dstew.access.command.application.ready.ReadyApplicationResult;
import uk.gov.justice.laa.dstew.access.command.application.update.ApplicationUpdateDetailsFactory;
import uk.gov.justice.laa.dstew.access.command.application.update.ApplicationUpdatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.update.UpdateApplicationCommand;
import uk.gov.justice.laa.dstew.access.exception.ApplicationAutoGrantOutcomeConflictException;
import uk.gov.justice.laa.dstew.access.exception.ApplicationVersionConflictException;
import uk.gov.justice.laa.dstew.access.exception.InvalidApplicationStateException;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;

/** Event-sourced consistency boundary for an Application and its owned child state. */
@EventSourced(tagKey = "ApplicationAggregate", idType = UUID.class)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ApplicationAggregate {

  private UUID applicationId;
  private final ApplicationState state = new ApplicationState();

  /**
   * Creates or idempotently re-identifies an Application.
   *
   * <p>On the first command for this aggregate ID, parses the request and emits {@link
   * ApplicationCreatedEvent}. On an identical retry (same serialised request and schema version),
   * returns the existing ID with no events. On a conflicting retry (same ID, different payload or
   * schema version), throws {@link
   * uk.gov.justice.laa.dstew.access.exception.ApplicationCreationConflictException} with no events.
   *
   * <p>Linking to a lead application is initiated asynchronously by {@link
   * uk.gov.justice.laa.dstew.access.command.application.linkedgroup.ApplicationGroupEventRouter}
   * after {@link ApplicationCreatedEvent} is processed; {@link ApplicationLinkedEvent} is no longer
   * emitted for new operations.
   */
  @CommandHandler
  UUID handle(
      CreateApplicationCommand command,
      ApplicationCreationDetailsFactory factory,
      ApplicationDataStore applicationDataStore,
      EventAppender eventAppender) {
    if (applicationId == null) {
      ApplicationCreationDetails details = factory.prepare(command);
      long applicationDataVersion = 0L;
      String fingerprint =
          applicationDataStore.append(command.applicationId(), applicationDataVersion, details);
      ApplicationDecider.decideCreate(
              state,
              command.applicationId(),
              command.schemaVersion(),
              fingerprint,
              details,
              applicationDataVersion)
          .forEach(e -> eventAppender.append(e));
    } else {
      String fingerprint = ApplicationDataStore.fingerprint(command.serialisedRequest());
      ApplicationDecider.decideCreate(
          state, command.applicationId(), command.schemaVersion(), fingerprint, null, 0L);
    }
    return applicationId;
  }

  /**
   * Validates the lead exists and records a group-formation request.
   *
   * <p>The {@code groupId} is derived deterministically from the lead's {@code applicationId} via
   * {@link UUID#nameUUIDFromBytes}. This ensures all applications that reference the same lead
   * converge on the same {@link LinkedApplicationGroupAggregate}, while remaining distinct from the
   * lead's UUID.
   */
  @CommandHandler
  void handle(CreateLinkedApplicationGroupCommand command, EventAppender eventAppender) {
    if (applicationId == null) {
      throw new ResourceNotFoundException(
          "No linked application found with Application ID: " + command.leadApplicationId());
    }
    UUID groupId =
        UUID.nameUUIDFromBytes(("linked-group:" + applicationId).getBytes(StandardCharsets.UTF_8));
    eventAppender.append(
        ApplicationDecider.decideCreateLinkedGroup(
            state, groupId, command.allMemberApplicationIds(), command.occurredAt()));
  }

  /** Proves that the targeted application exists. */
  @CommandHandler
  void handle(ValidateApplicationExistsCommand command) {
    if (applicationId == null) {
      throw new ResourceNotFoundException(
          "No linked application found with Application ID: " + command.applicationId());
    }
    // Application exists — no events, no state change.
  }

  /** Validates and stores a decision as the next immutable application-data version. */
  @CommandHandler
  void handle(
      MakeApplicationDecisionCommand command,
      ApplicationDataStore applicationDataStore,
      EventAppender eventAppender) {
    requireApplicationExists(command.applicationId());
    if (command.fromAutoGrantOutcome() && state.autoGranted == AutoGrantedState.MANUAL) {
      throw new ApplicationAutoGrantOutcomeConflictException(command.applicationId());
    }
    if (command.fromAutoGrantOutcome() && !"APPLICATION_SUBMITTED".equals(state.status)) {
      throw new InvalidApplicationStateException(command.applicationId(), state.status);
    }
    if (command.fromAutoGrantOutcome() && state.autoGranted == AutoGrantedState.AUTOGRANTED) {
      var recorded = applicationDataStore.get(applicationId, state.applicationDataVersion);
      if (Objects.equals(recorded.decisionSerialisedRequest(), command.serialisedRequest())) {
        return;
      }
      throw new ApplicationAutoGrantOutcomeConflictException(command.applicationId());
    }
    if (!command.fromAutoGrantOutcome()
        && command.expectedApplicationVersion() != state.applicationVersion) {
      throw new ApplicationVersionConflictException(
          command.applicationId(), command.expectedApplicationVersion());
    }
    var current = applicationDataStore.get(applicationId, state.applicationDataVersion);
    MakeApplicationDecisionCommand effectiveCommand = command;
    if (command.fromAutoGrantOutcome()) {
      var grantedProceedings =
          current.proceedings().stream()
              .map(
                  proceeding ->
                      new MakeDecisionProceeding(
                          proceeding.getId(), "GRANTED", null, "Autogranted"))
              .toList();
      effectiveCommand =
          new MakeApplicationDecisionCommand(
              command.applicationId(),
              state.applicationVersion,
              "GRANTED",
              true,
              grantedProceedings,
              command.certificate(),
              command.serialisedRequest(),
              "Autogranted",
              command.occurredAt(),
              true);
    }
    ApplicationDecisionMadeEvent event =
        ApplicationDecider.decideDecision(state, effectiveCommand, current);

    var meritsDecisions =
        new HashMap<>(
            current.meritsDecisions() == null ? java.util.Map.of() : current.meritsDecisions());
    effectiveCommand
        .proceedings()
        .forEach(
            proceeding ->
                meritsDecisions.put(
                    proceeding.proceedingId(),
                    new ApplicationMeritsDecision(
                        proceeding.decision(), proceeding.reason(), proceeding.justification())));
    long nextVersion = state.applicationDataVersion + 1;
    var updated =
        current.withDecision(
            effectiveCommand.overallDecision(),
            AutoGrantedState.fromDecisionFlag(effectiveCommand.autoGranted()),
            meritsDecisions,
            "GRANTED".equals(effectiveCommand.overallDecision())
                ? effectiveCommand.certificate()
                : null,
            command.serialisedRequest(),
            effectiveCommand.eventDescription());
    applicationDataStore.append(
        applicationId, nextVersion, updated, command.serialisedRequest(), command.occurredAt());

    eventAppender.append(event);
  }

  /** Assigns a caseworker and stores free-text audit data outside the event stream. */
  @CommandHandler
  void handle(
      AssignCaseworkerToApplicationCommand command,
      ApplicationDataStore applicationDataStore,
      EventAppender eventAppender) {
    requireApplicationExists(command.applicationId());
    ApplicationAssignedToCaseworkerEvent event = ApplicationDecider.decideAssign(state, command);
    var current = applicationDataStore.get(applicationId, state.applicationDataVersion);
    long nextDataVersion = state.applicationDataVersion + 1;
    applicationDataStore.append(
        applicationId,
        nextDataVersion,
        current.withAssignment(command.eventDescription()),
        command.serialisedRequest(),
        command.occurredAt());
    eventAppender.append(event);
  }

  /** Removes the assigned caseworker and stores free-text audit data outside the event stream. */
  @CommandHandler
  void handle(
      UnassignCaseworkerFromApplicationCommand command,
      ApplicationDataStore applicationDataStore,
      EventAppender eventAppender) {
    requireApplicationExists(command.applicationId());
    if (state.caseworkerId == null) {
      return;
    }
    ApplicationUnassignedFromCaseworkerEvent event =
        ApplicationDecider.decideUnassign(state, command);
    var current = applicationDataStore.get(applicationId, state.applicationDataVersion);
    long nextDataVersion = state.applicationDataVersion + 1;
    applicationDataStore.append(
        applicationId,
        nextDataVersion,
        current.withAssignment(command.eventDescription()),
        command.serialisedRequest(),
        command.occurredAt());
    eventAppender.append(event);
  }

  /** Appends a note to the application's immutable data without advancing the decision version. */
  @CommandHandler
  void handle(
      CreateNoteCommand command,
      ApplicationDataStore applicationDataStore,
      EventAppender eventAppender) {
    requireApplicationExists(command.applicationId());
    NoteCreatedEvent event = ApplicationDecider.decideNote(state, command);
    var current = applicationDataStore.get(applicationId, state.applicationDataVersion);
    applicationDataStore.append(
        applicationId,
        event.applicationDataVersion(),
        current.withNote(command.noteText(), command.occurredAt()),
        command.serialisedNoteRequest(),
        command.occurredAt());
    eventAppender.append(event);
  }

  /** Stores {@code autoGranted=MANUAL} as the next immutable Application-data version. */
  @CommandHandler
  ReadyApplicationResult handle(
      MarkApplicationReadyCommand command,
      ApplicationDataStore applicationDataStore,
      EventAppender eventAppender) {
    requireApplicationExists(command.applicationId());
    ReadyApplicationResult result = ApplicationDecider.decideReady(state, command);
    if (result == ReadyApplicationResult.ALREADY_RECORDED) {
      return result;
    }

    var current = applicationDataStore.get(applicationId, state.applicationDataVersion);
    long nextApplicationVersion = state.applicationVersion + 1;
    long nextDataVersion = state.applicationDataVersion + 1;
    applicationDataStore.append(
        applicationId,
        nextDataVersion,
        current.withManualAssessmentRequired(),
        command.serialisedRequest(),
        command.occurredAt());
    eventAppender.append(
        new ApplicationReadyForManualAssessmentEvent(
            applicationId, nextApplicationVersion, nextDataVersion, command.occurredAt()));
    return result;
  }

  /** Replaces Application content and appends a thin, replayable update event. */
  @CommandHandler
  void handle(
      UpdateApplicationCommand command,
      ApplicationDataStore applicationDataStore,
      ApplicationUpdateDetailsFactory detailsFactory,
      EventAppender eventAppender) {
    requireApplicationExists(command.applicationId());
    var current = applicationDataStore.get(applicationId, state.applicationDataVersion);
    String nextStatus = command.status() == null ? state.status : command.status();
    boolean enteringSubmitted =
        !"APPLICATION_SUBMITTED".equals(state.status) && "APPLICATION_SUBMITTED".equals(nextStatus);
    var updated = detailsFactory.prepare(command, current, enteringSubmitted);
    ApplicationUpdatedEvent event = ApplicationDecider.decideUpdate(state, command);
    applicationDataStore.append(
        applicationId,
        event.applicationDataVersion(),
        updated,
        command.serialisedRequest(),
        command.occurredAt());
    eventAppender.append(event);
  }

  private void requireApplicationExists(UUID requestedApplicationId) {
    if (applicationId == null) {
      throw new ResourceNotFoundException(
          "No application found with Application ID: " + requestedApplicationId);
    }
  }

  @EventSourcingHandler
  void on(LinkedApplicationGroupRequested event) {
    ApplicationEvolve.apply(state, event);
  }

  @EventSourcingHandler
  void on(ApplicationCreatedEvent event) {
    ApplicationEvolve.apply(state, event);
    this.applicationId = state.applicationId;
  }

  @EventSourcingHandler
  void on(ApplicationDecisionMadeEvent event) {
    ApplicationEvolve.apply(state, event);
  }

  @EventSourcingHandler
  void on(ApplicationAssignedToCaseworkerEvent event) {
    ApplicationEvolve.apply(state, event);
  }

  @EventSourcingHandler
  void on(ApplicationUnassignedFromCaseworkerEvent event) {
    ApplicationEvolve.apply(state, event);
  }

  @EventSourcingHandler
  void on(NoteCreatedEvent event) {
    ApplicationEvolve.apply(state, event);
  }

  @EventSourcingHandler
  void on(ApplicationReadyForManualAssessmentEvent event) {
    ApplicationEvolve.apply(state, event);
  }

  @EventSourcingHandler
  void on(ApplicationUpdatedEvent event) {
    ApplicationEvolve.apply(state, event);
  }

  @EventSourcingHandler
  void on(ApplicationLinkedEvent event) {
    ApplicationEvolve.apply(state, event);
  }

  @EntityCreator
  protected ApplicationAggregate() {
    // Required by Axon when rebuilding the aggregate from its event stream.
  }
}
