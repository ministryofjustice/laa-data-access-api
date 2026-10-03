package uk.gov.justice.laa.dstew.access.command.application.priorauthority.handler;

import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.annotation.InjectEntity;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.UploadDocument;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityAggregate;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDecider;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentUploadCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDraftStore;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

/** Handles uploads of documents to a prior-authority draft. */
@Component
public class PriorAuthorityDocumentUploadCommandHandler {

  /** Uploads a document to the prior-authority draft and emits the corresponding event. */
  @CommandHandler
  public UUID handle(
      PriorAuthorityDocumentUploadCommand command,
      PriorAuthorityDraftStore draftStore,
      @InjectEntity(idProperty = "priorAuthorityId") PriorAuthorityAggregate priorAuthority,
      EventAppender eventAppender) {

    requireDraftLifecycle(priorAuthority);
    if (command.originalFilename() == null || command.originalFilename().isBlank()) {
      throw new ValidationException(java.util.List.of("Original filename is required"));
    }
    if (priorAuthority.getState().getUploadedDocuments().stream()
        .anyMatch(document -> document.documentId().equals(command.documentId()))) {
      throw new ValidationException(java.util.List.of("Document ID already records an upload"));
    }

    PriorAuthorityDataPayload existingDraft = requireDraft(command.priorAuthorityId(), draftStore);
    PriorAuthorityDataPayload updatedPayload =
        existingDraft.withDocumentFilename(command.documentId(), command.originalFilename());
    draftStore.upsert(
        command.priorAuthorityId(),
        existingDraft.applicationId(),
        updatedPayload,
        existingDraft.serialisedRequest(),
        command.occurredAt());
    eventAppender.append(
        PriorAuthorityDecider.decideDocumentUploaded(command, priorAuthority.getApplicationId()));
    return command.documentId();
  }

    private static void requireDraftLifecycle(PriorAuthorityAggregate priorAuthority) {
        if (priorAuthority.getState().isSubmitted()) {
            throw new ValidationException(
                    java.util.List.of("Documents can only be changed on a prior authority draft"));
        }
    }

    private static PriorAuthorityDataPayload requireDraft(
            UUID priorAuthorityId, PriorAuthorityDraftStore draftStore) {
        return draftStore
                .find(priorAuthorityId)
                .orElseThrow(
                        () ->
                                new ResourceNotFoundException(
                                        "Prior Authority draft not found: " + priorAuthorityId));
  }
}
