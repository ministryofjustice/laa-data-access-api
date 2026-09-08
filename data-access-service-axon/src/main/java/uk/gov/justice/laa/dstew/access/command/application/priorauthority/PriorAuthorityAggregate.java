package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator;
import org.axonframework.extension.spring.stereotype.EventSourced;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataStore;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDraftStore;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssignmentConflictException;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemUnassigned;
import uk.gov.justice.laa.dstew.access.command.worklist.assign.DirectPriorAuthorityWorkItemAssignmentCommand;
import uk.gov.justice.laa.dstew.access.command.worklist.unassign.DirectPriorAuthorityWorkItemUnassignmentCommand;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityDocument;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityType;
import uk.gov.justice.laa.dstew.access.exception.PriorAuthorityCreationConflictException;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.model.DocumentUploadResponse;
import uk.gov.justice.laa.dstew.access.service.sds.SdsService;
import uk.gov.justice.laa.dstew.access.validation.JsonSchemaValidator;

/**
 * Event-sourced consistency boundary for a PriorAuthority submission.
 *
 * <p>On the first command for this aggregate ID, persists version 0 of the sensitive data and emits
 * {@link PriorAuthorityDraftStartedEvent}.
 */
@EventSourced(tagKey = "PriorAuthorityAggregate", idType = UUID.class)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class PriorAuthorityAggregate {

  private UUID priorAuthorityId;
  private final PriorAuthorityState state = new PriorAuthorityState();

  @CommandHandler
  void handle(
      CreatePriorAuthorityDraftCommand command,
      PriorAuthorityDraftStore draftStore,
      EventAppender eventAppender) {
    if (state.priorAuthorityId != null) {
      throw new PriorAuthorityCreationConflictException(command.priorAuthorityId());
    }
    PriorAuthorityDataPayload payload =
        new PriorAuthorityDataPayload(
            command.priorAuthorityId(),
            command.applicationId(),
            command.content(),
            command.serialisedRequest(),
            command.occurredAt());
    draftStore.upsert(
        command.priorAuthorityId(),
        command.applicationId(),
        payload,
        command.serialisedRequest(),
        command.occurredAt());
    eventAppender.append(PriorAuthorityDecider.decideStartDraft(command));
  }

  @CommandHandler
  void handle(UpdatePriorAuthorityDraftCommand command, PriorAuthorityDraftStore draftStore) {
    PriorAuthorityDataPayload existingDraft =
        draftStore
            .find(command.priorAuthorityId())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Prior Authority %s not found".formatted(command.priorAuthorityId())));
    PriorAuthorityDataPayload payload =
        new PriorAuthorityDataPayload(
            command.priorAuthorityId(),
            state.applicationId,
            new PriorAuthorityContent(
                PriorAuthorityType.valueOf(state.priorAuthorityType),
                command.content().justification(),
                command.content().expertDetails(),
                command.content().counselDetails(),
                command.content().disbursementDetails(),
                existingDraft.content().uploadedDocuments()),
            command.serialisedRequest(),
            command.occurredAt());
    draftStore.upsert(
        command.priorAuthorityId(),
        state.applicationId,
        payload,
        command.serialisedRequest(),
        command.occurredAt());
  }

  @CommandHandler
  UUID handle(
      PriorAuthorityDocumentUploadCommand command,
      SdsService sdsService,
      PriorAuthorityDraftStore draftStore,
      EventAppender eventAppender) {
    UUID documentId = UUID.randomUUID();
    DocumentUploadResponse sdsResponse =
        sdsService.savePriorAuthorityFile(command.priorAuthorityId(), documentId, command.file());
    PriorAuthorityDataPayload existingDraft =
        draftStore
            .find(command.priorAuthorityId())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Prior Authority %s not found".formatted(command.priorAuthorityId())));
    List<PriorAuthorityDocument> existingDocuments =
        existingDraft.content().uploadedDocuments() == null
            ? new ArrayList<>()
            : new ArrayList<>(existingDraft.content().uploadedDocuments());
    existingDocuments.add(
        new PriorAuthorityDocument(
            documentId,
            command.file().getOriginalFilename(),
            command.file().getContentType(),
            command.file().getSize(),
            command.occurredAt()));

    PriorAuthorityContent updatedContent =
        new PriorAuthorityContent(
            existingDraft.content().priorAuthorityType(),
            existingDraft.content().justification(),
            existingDraft.content().expertDetails(),
            existingDraft.content().counselDetails(),
            existingDraft.content().disbursementDetails(),
            List.copyOf(existingDocuments));

    PriorAuthorityDataPayload updatedPayload =
        new PriorAuthorityDataPayload(
            existingDraft.priorAuthorityId(),
            existingDraft.applicationId(),
            updatedContent,
            command.serialisedRequest(),
            existingDraft.submittedAt());
    draftStore.upsert(
        command.priorAuthorityId(),
        existingDraft.applicationId(),
        updatedPayload,
        command.serialisedRequest(),
        command.occurredAt());
    eventAppender.append(
        PriorAuthorityDecider.decideDocumentUploaded(
            command, documentId, sdsResponse == null ? null : sdsResponse.getChecksum()));
    return documentId;
  }

  @CommandHandler
  void handle(
      SubmitPriorAuthorityDraftCommand command,
      PriorAuthorityDraftStore draftStore,
      PriorAuthorityDataStore dataStore,
      JsonSchemaValidator jsonSchemaValidator,
      EventAppender eventAppender) {
    PriorAuthorityDataPayload payload =
        draftStore
            .find(command.priorAuthorityId())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Prior Authority %s not found".formatted(command.priorAuthorityId())));
    jsonSchemaValidator.validate(payload.content(), "PriorAuthority.json", state.schemaVersion);
    dataStore.append(
        command.priorAuthorityId(),
        0L,
        state.applicationId,
        payload,
        payload.serialisedRequest(),
        command.occurredAt());
    eventAppender.append(PriorAuthorityDecider.decideSubmit(command, state));
    draftStore.delete(command.priorAuthorityId());
  }

  /** Assigns a newly created direct PA work item after durable route resolution. */
  @CommandHandler
  void handle(DirectPriorAuthorityWorkItemAssignmentCommand command, EventAppender eventAppender) {
    validateWorkItem(command.workItemId(), command.expectedAssignmentVersion());
    if (state.caseworkerId != null) {
      throw new WorkItemAssignmentConflictException(command.workItemId(), "it is already assigned");
    }
    eventAppender.append(
        new WorkItemAssigned(
            command.workItemId(),
            WorkItemType.PRIOR_AUTHORITY,
            state.dataVersion,
            state.assignmentVersion + 1,
            command.caseworkerId(),
            command.occurredAt()));
  }

  /** Explicitly clears a direct PA assignment; already-open work is a conflict. */
  @CommandHandler
  void handle(
      DirectPriorAuthorityWorkItemUnassignmentCommand command, EventAppender eventAppender) {
    validateWorkItem(command.workItemId(), command.expectedAssignmentVersion());
    if (state.caseworkerId == null) {
      throw new WorkItemAssignmentConflictException(
          command.workItemId(), "it is already unassigned");
    }
    eventAppender.append(
        new WorkItemUnassigned(
            command.workItemId(),
            WorkItemType.PRIOR_AUTHORITY,
            state.dataVersion,
            state.assignmentVersion + 1,
            command.occurredAt()));
  }

  private void validateWorkItem(UUID workItemId, long expectedAssignmentVersion) {
    if (priorAuthorityId == null || !priorAuthorityId.equals(workItemId)) {
      throw new ResourceNotFoundException(
          "No prior-authority work item found with id: " + workItemId);
    }
    if (expectedAssignmentVersion != state.assignmentVersion) {
      throw new WorkItemAssignmentConflictException(workItemId, "the assignment version is stale");
    }
  }

  @EventSourcingHandler
  void on(PriorAuthorityDraftStartedEvent event) {
    PriorAuthorityEvolve.apply(state, event);
    this.priorAuthorityId = state.priorAuthorityId;
  }

  @EventSourcingHandler
  void on(PriorAuthoritySubmittedEvent event) {
    PriorAuthorityEvolve.apply(state, event);
    this.priorAuthorityId = state.priorAuthorityId;
  }

  @EventSourcingHandler
  void on(PriorAuthorityDocumentUploadedEvent event) {
    PriorAuthorityEvolve.apply(state, event);
    this.priorAuthorityId = state.priorAuthorityId;
  }

  @EventSourcingHandler
  void on(WorkItemAssigned event) {
    PriorAuthorityEvolve.apply(state, event);
  }

  @EventSourcingHandler
  void on(WorkItemUnassigned event) {
    PriorAuthorityEvolve.apply(state, event);
  }

  @EntityCreator
  protected PriorAuthorityAggregate() {
    // Required by Axon when rebuilding the aggregate from its event stream.
  }
}
