package uk.gov.justice.laa.dstew.access.command.application.priorauthority.handler;

import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.annotation.InjectEntity;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.UploadDocument;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityAggregate;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDecider;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentDeleteCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDraftStore;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

/** Handles deletion of documents from a prior-authority draft. */
@Component
public class PriorAuthorityDocumentDeleteCommandHandler {

  /** Deletes a document from the prior-authority draft and emits the corresponding event. */
  @CommandHandler
    public UploadDocument handle(
      PriorAuthorityDocumentDeleteCommand command,
      PriorAuthorityDraftStore draftStore,
      @InjectEntity(idProperty = "priorAuthorityId") PriorAuthorityAggregate priorAuthority,
      EventAppender eventAppender) {

    requireDraftLifecycle(priorAuthority);
    UploadDocument document =
        priorAuthority.getState().getUploadedDocuments().stream()
            .filter(uploaded -> uploaded.documentId().equals(command.documentId()))
            .filter(uploaded -> !uploaded.deleted())
            .findFirst()
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Document %s not found for Prior Authority %s"
                            .formatted(command.documentId(), command.priorAuthorityId())));

    PriorAuthorityDataPayload existingDraft = requireDraft(command.priorAuthorityId(), draftStore);
    draftStore.upsert(
        command.priorAuthorityId(),
        existingDraft.applicationId(),
        existingDraft.withoutDocumentFilename(command.documentId()),
        existingDraft.serialisedRequest(),
        command.occurredAt());
    eventAppender.append(
        PriorAuthorityDecider.decideDocumentDeleted(command, priorAuthority.getApplicationId()));
    return document;
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
