package uk.gov.justice.laa.dstew.access.command.application;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator;
import org.axonframework.extension.spring.stereotype.EventSourced;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import uk.gov.justice.laa.dstew.access.applicationcontent.DecisionValue;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDraftPayload;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDraftStore;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationMeritsDecision;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.decision.MakeApplicationDecisionCommand;
import uk.gov.justice.laa.dstew.access.command.application.decision.MakeDecisionProceeding;
import uk.gov.justice.laa.dstew.access.command.application.decision.RecordAutoGrantedOutcomeCommand;
import uk.gov.justice.laa.dstew.access.command.application.draft.ApplicationDraftStartedEvent;
import uk.gov.justice.laa.dstew.access.command.application.draft.CreateApplicationDraftCommand;
import uk.gov.justice.laa.dstew.access.command.application.draft.SubmitApplicationDraftCommand;
import uk.gov.justice.laa.dstew.access.command.application.note.CreateNoteCommand;
import uk.gov.justice.laa.dstew.access.command.application.note.NoteCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.ValidateApplicationGrantedCommand;
import uk.gov.justice.laa.dstew.access.command.application.ready.ApplicationReadyForManualAssessmentEvent;
import uk.gov.justice.laa.dstew.access.command.application.ready.MarkApplicationReadyCommand;
import uk.gov.justice.laa.dstew.access.command.application.ready.ReadyApplicationResult;
import uk.gov.justice.laa.dstew.access.command.application.update.ApplicationUpdateDetailsFactory;
import uk.gov.justice.laa.dstew.access.command.application.update.ApplicationUpdatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.update.UpdateApplicationCommand;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssignmentConflictException;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemUnassigned;
import uk.gov.justice.laa.dstew.access.command.worklist.assign.DirectGroupWorkItemAssignmentCommand;
import uk.gov.justice.laa.dstew.access.command.worklist.assign.DirectWorkItemAssignmentCommand;
import uk.gov.justice.laa.dstew.access.command.worklist.unassign.DirectWorkItemUnassignmentCommand;
import uk.gov.justice.laa.dstew.access.exception.ApplicationAutoGrantOutcomeConflictException;
import uk.gov.justice.laa.dstew.access.exception.ApplicationCreationConflictException;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.util.PayloadFingerprint;
import uk.gov.justice.laa.dstew.access.validation.JsonSchemaValidator;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

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
   * <p>Application linking is handled explicitly by the application-link endpoint after creation.
   */
  @CommandHandler
  UUID handle(
      CreateApplicationCommand command,
      ApplicationCreationDetailsFactory factory,
      ApplicationDataStore applicationDataStore,
      JsonSchemaValidator jsonSchemaValidator,
      EventAppender eventAppender) {
    jsonSchemaValidator.validate(
        command.applicationContent(), command.schemaName(), command.schemaVersion());
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
          .forEach(eventAppender::append);
    } else {
      String fingerprint = ApplicationDataStore.fingerprint(command.serialisedRequest());
      ApplicationDecider.decideCreate(
          state, command.applicationId(), command.schemaVersion(), fingerprint, null, 0L);
    }
    return applicationId;
  }

  /**
   * Creates an Application draft or handles an idempotent retry.
   *
   * <p>On the first command for this aggregate ID, validates the content and emits an {@link
   * ApplicationDraftStartedEvent}. An identical retry returns the existing ID without emitting an
   * event. A retry with a different payload or schema version throws {@link
   * ApplicationCreationConflictException}.
   *
   * @throws ApplicationCreationConflictException if a retry has a different payload or schema
   *     version
   * @throws ValidationException if the content fails semantic validation
   */
  @CommandHandler
  UUID handle(
      CreateApplicationDraftCommand command,
      ApplicationCreationDetailsFactory factory,
      ApplicationDraftStore draftStore,
      JsonSchemaValidator jsonSchemaValidator,
      EventAppender eventAppender) {
    jsonSchemaValidator.validate(
        command.applicationContent(), command.schemaName(), command.schemaVersion());
    if (applicationId == null) {
      factory.validate(command.applicationContent());
      ApplicationDraftPayload payload =
          new ApplicationDraftPayload(
              command.status(),
              command.laaReference(),
              command.applicationContent(),
              command.serialisedRequest(),
              command.potentialDuplicates());
      String fingerprint =
          draftStore.upsert(
              command.applicationId(), payload, command.serialisedRequest(), command.occurredAt());
      ApplicationDecider.decideStartDraft(
              state,
              command.applicationId(),
              command.schemaVersion(),
              fingerprint,
              command.occurredAt())
          .forEach(eventAppender::append);
    } else {
      String fingerprint = PayloadFingerprint.compute(command.serialisedRequest());
      ApplicationDecider.decideStartDraft(
          state,
          command.applicationId(),
          command.schemaVersion(),
          fingerprint,
          command.occurredAt());
    }
    return command.applicationId();
  }

  /**
   * Completes an existing Application draft and emits an {@link ApplicationCreatedEvent}.
   *
   * @throws ResourceNotFoundException if no draft exists for this ID
   * @throws ApplicationCreationConflictException if this ID has already been created or submitted
   */
  @CommandHandler
  UUID handle(
      SubmitApplicationDraftCommand command,
      ApplicationDraftStore draftStore,
      ApplicationCreationDetailsFactory detailsFactory,
      ApplicationDataStore applicationDataStore,
      EventAppender eventAppender) {
    ApplicationDraftPayload draft = requireDraft(command.applicationId(), draftStore);

    ApplicationCreationDetails details =
        detailsFactory.prepare(
            draft.status(),
            draft.laaReference(),
            draft.applicationContent(),
            draft.serialisedRequest(),
            state.schemaVersion,
            draft.potentialDuplicates());
    long applicationDataVersion = 0L;
    ApplicationDataPayload payload = ApplicationDataPayload.from(details);
    for (var filename : draft.documentFilenames().entrySet()) {
      payload = payload.withDocumentFilename(filename.getKey(), filename.getValue());
    }
    String fingerprint =
        applicationDataStore.append(
            command.applicationId(),
            applicationDataVersion,
            payload,
            details.serialisedRequest(),
            details.occurredAt());
    eventAppender.append(
        ApplicationDecider.decideSubmitDraft(
            command.applicationId(), applicationDataVersion, fingerprint, details));
    draftStore.delete(command.applicationId());
    return command.applicationId();
  }

  /** Validates that the targeted application has an overall decision of {@code GRANTED}. */
  @CommandHandler
  void handle(ValidateApplicationGrantedCommand command) {
    if (applicationId == null) {
      throw new ResourceNotFoundException(
          "No application found with Application ID: " + command.applicationId());
    }
    ApplicationDecider.validateGranted(state);
  }

  /** Validates and stores a decision as the next immutable application-data version. */
  @CommandHandler
  void handle(
      MakeApplicationDecisionCommand command,
      ApplicationDataStore applicationDataStore,
      EventAppender eventAppender) {
    requireApplicationExists(command.applicationId());
    validateManualDecision(command);
    recordManualDecision(command, applicationDataStore, eventAppender);
  }

  /** Records the external service's automatic-grant outcome as a complete system decision. */
  @CommandHandler
  void handle(
      RecordAutoGrantedOutcomeCommand command,
      ApplicationDataStore applicationDataStore,
      EventAppender eventAppender) {
    requireApplicationExists(command.applicationId());
    validateAutomaticOutcome(command, applicationDataStore);
    recordAutomaticGrant(command, applicationDataStore, eventAppender);
  }

  /**
   * Handles a generic direct assignment once durable routing selected this standalone aggregate.
   */
  @CommandHandler
  void handle(DirectWorkItemAssignmentCommand command, EventAppender eventAppender) {
    requireApplicationExists(command.workItemId());
    validateDirectWorkItem(command.workItemId(), command.expectedAssignmentVersion());
    if (state.caseworkerId != null) {
      throw new WorkItemAssignmentConflictException(command.workItemId(), "it is already assigned");
    }
    long nextAssignmentVersion = state.assignmentVersion + 1;
    eventAppender.append(
        new WorkItemAssigned(
            command.workItemId(),
            WorkItemType.APPLICATION,
            state.applicationVersion,
            nextAssignmentVersion,
            command.caseworkerId(),
            command.occurredAt()));
  }

  /**
   * Applies a linked-group assignment to this application, replacing its current assignee.
   *
   * <p>Skips applications that are not manual and undecided, or are already assigned to the
   * requested caseworker. This allows the group assignment to continue when some members cannot
   * accept the assignment. When {@code expectedAssignmentVersion} is supplied - which only happens
   * for the single item the caseworker was actually looking at - a stale version is rejected.
   */
  @CommandHandler
  void handle(DirectGroupWorkItemAssignmentCommand command, EventAppender eventAppender) {
    if (applicationId == null
        || state.autoGranted != AutoGrantedState.MANUAL
        || state.overallDecision != null) {
      return;
    }
    if (command.expectedAssignmentVersion() != null
        && command.expectedAssignmentVersion() != state.assignmentVersion) {
      throw new WorkItemAssignmentConflictException(
          command.workItemId(), "the assignment version is stale");
    }
    if (Objects.equals(state.caseworkerId, command.caseworkerId())) {
      return;
    }
    long nextAssignmentVersion = state.assignmentVersion + 1;
    eventAppender.append(
        new WorkItemAssigned(
            command.workItemId(),
            WorkItemType.APPLICATION,
            state.applicationVersion,
            nextAssignmentVersion,
            command.caseworkerId(),
            command.occurredAt()));
  }

  /** Handles explicit generic direct unassignment; an already-open item is a conflict. */
  @CommandHandler
  void handle(DirectWorkItemUnassignmentCommand command, EventAppender eventAppender) {
    requireApplicationExists(command.workItemId());
    validateDirectWorkItem(command.workItemId(), command.expectedAssignmentVersion());
    if (state.caseworkerId == null) {
      throw new WorkItemAssignmentConflictException(
          command.workItemId(), "it is already unassigned");
    }
    long nextAssignmentVersion = state.assignmentVersion + 1;
    eventAppender.append(
        new WorkItemUnassigned(
            command.workItemId(),
            WorkItemType.APPLICATION,
            state.applicationVersion,
            nextAssignmentVersion,
            command.occurredAt()));
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

  /** Records metadata for an application document that SDS has already accepted. */
  @CommandHandler
  UUID handle(
      ApplicationDocumentUploadCommand command,
      ApplicationDraftStore draftStore,
      EventAppender eventAppender) {
    requireApplicationDraft(command.applicationId());
    if (command.originalFilename() == null || command.originalFilename().isBlank()) {
      throw new ValidationException(List.of("Original filename is required"));
    }
    var current = requireDraft(command.applicationId(), draftStore);
    var existing =
        state.uploadedDocuments.stream()
            .map(UploadDocument::documentId)
            .anyMatch(id -> id.equals(command.documentId()));
    if (existing) {
      throw new ValidationException(List.of("Document ID already records a different upload"));
    }

    draftStore.upsert(
        applicationId,
        current.withDocumentFilename(command.documentId(), command.originalFilename()),
        current.serialisedRequest(),
        command.uploadedAt());
    eventAppender.append(
        new ApplicationDocumentUploadedEvent(
            command.applicationId(),
            command.documentId(),
            command.documentType(),
            command.uploadedAt(),
            command.size(),
            command.contentType(),
            command.checksum(),
            command.sourceService()));
    return command.documentId();
  }

  private void requireApplicationDraft(UUID requestedApplicationId) {
    requireApplicationExists(requestedApplicationId);
    if (state.status != null) {
      throw new ValidationException(
          List.of("Documents can only be uploaded to an application draft"));
    }
  }

  private void validateManualDecision(MakeApplicationDecisionCommand command) {
    ApplicationDecider.validateManualDecisionAssignment(state, command);
  }

  private void validateAutomaticOutcome(
      RecordAutoGrantedOutcomeCommand command, ApplicationDataStore applicationDataStore) {
    if (state.autoGranted == AutoGrantedState.MANUAL) {
      throw new ApplicationAutoGrantOutcomeConflictException(command.applicationId());
    }
    if (state.autoGranted != AutoGrantedState.AUTOGRANTED) {
      return;
    }

    var recorded = applicationDataStore.get(applicationId, state.applicationDataVersion);
    if (!Objects.equals(recorded.decisionSerialisedRequest(), command.serialisedRequest())) {
      throw new ApplicationAutoGrantOutcomeConflictException(command.applicationId());
    }
  }

  private void recordAutomaticGrant(
      RecordAutoGrantedOutcomeCommand command,
      ApplicationDataStore applicationDataStore,
      EventAppender eventAppender) {
    if (state.autoGranted == AutoGrantedState.AUTOGRANTED) {
      return;
    }

    var current = applicationDataStore.get(applicationId, state.applicationDataVersion);
    recordDecision(
        automaticGrantDecision(command, current),
        AutoGrantedState.AUTOGRANTED,
        current,
        applicationDataStore,
        eventAppender);
  }

  private MakeApplicationDecisionCommand automaticGrantDecision(
      RecordAutoGrantedOutcomeCommand command, ApplicationDataPayload current) {
    var grantedProceedings =
        current.proceedings().stream()
            .map(
                proceeding ->
                    new MakeDecisionProceeding(
                        proceeding.getId(), DecisionValue.GRANTED.name(), null, "Autogranted"))
            .toList();
    return new MakeApplicationDecisionCommand(
        command.applicationId(),
        null,
        state.applicationVersion,
        DecisionValue.GRANTED.name(),
        grantedProceedings,
        command.certificate(),
        command.serialisedRequest(),
        "Autogranted",
        command.occurredAt());
  }

  private void recordManualDecision(
      MakeApplicationDecisionCommand command,
      ApplicationDataStore applicationDataStore,
      EventAppender eventAppender) {
    var current = applicationDataStore.get(applicationId, state.applicationDataVersion);
    recordDecision(command, AutoGrantedState.MANUAL, current, applicationDataStore, eventAppender);
  }

  private void recordDecision(
      MakeApplicationDecisionCommand command,
      AutoGrantedState autoGranted,
      ApplicationDataPayload current,
      ApplicationDataStore applicationDataStore,
      EventAppender eventAppender) {
    ApplicationDecisionMadeEvent decision =
        ApplicationDecider.decideDecision(state, command, current, autoGranted);
    long nextVersion = state.applicationDataVersion + 1;
    var updated =
        current.withDecision(
            command.overallDecision(),
            autoGranted,
            meritsDecisions(current, command),
            DecisionValue.GRANTED.name().equals(command.overallDecision())
                ? command.certificate()
                : null,
            command.serialisedRequest(),
            command.eventDescription());
    applicationDataStore.append(
        applicationId, nextVersion, updated, command.serialisedRequest(), command.occurredAt());

    eventAppender.append(decision);
  }

  private HashMap<UUID, ApplicationMeritsDecision> meritsDecisions(
      ApplicationDataPayload current, MakeApplicationDecisionCommand command) {
    var meritsDecisions =
        new HashMap<>(
            current.meritsDecisions() == null ? java.util.Map.of() : current.meritsDecisions());
    command
        .proceedings()
        .forEach(
            proceeding ->
                meritsDecisions.put(
                    proceeding.proceedingId(),
                    new ApplicationMeritsDecision(
                        proceeding.decision(), proceeding.reason(), proceeding.justification())));
    return meritsDecisions;
  }

  private void requireApplicationExists(UUID requestedApplicationId) {
    if (applicationId == null) {
      throw new ResourceNotFoundException(
          "No application found with Application ID: " + requestedApplicationId);
    }
  }

  private static ApplicationDraftPayload requireDraft(
      UUID requestedApplicationId, ApplicationDraftStore draftStore) {
    return draftStore
        .find(requestedApplicationId)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "No application draft found with Application ID: " + requestedApplicationId));
  }

  private void validateDirectWorkItem(UUID workItemId, long expectedAssignmentVersion) {
    if (!applicationId.equals(workItemId)) {
      throw new ResourceNotFoundException("No application work item found with id: " + workItemId);
    }
    if (state.autoGranted != AutoGrantedState.MANUAL || state.overallDecision != null) {
      throw new ResourceNotFoundException("Application work item is not active: " + workItemId);
    }
    if (expectedAssignmentVersion != state.assignmentVersion) {
      throw new WorkItemAssignmentConflictException(workItemId, "the assignment version is stale");
    }
  }

  public boolean isGranted() {
    return "GRANTED".equals(state.overallDecision);
  }

  @EventSourcingHandler
  void on(ApplicationCreatedEvent event) {
    ApplicationEvolve.apply(state, event);
    this.applicationId = state.applicationId;
  }

  @EventSourcingHandler
  void on(ApplicationDraftStartedEvent event) {
    ApplicationEvolve.apply(state, event);
    this.applicationId = state.applicationId;
  }

  @EventSourcingHandler
  void on(ApplicationDecisionMadeEvent event) {
    ApplicationEvolve.apply(state, event);
  }

  @EventSourcingHandler
  void on(WorkItemAssigned event) {
    ApplicationEvolve.apply(state, event);
  }

  @EventSourcingHandler
  void on(WorkItemUnassigned event) {
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
  void on(ApplicationDocumentUploadedEvent event) {
    ApplicationEvolve.apply(state, event);
  }

  @EntityCreator
  protected ApplicationAggregate() {
    // Required by Axon when rebuilding the aggregate from its event stream.
  }
}
