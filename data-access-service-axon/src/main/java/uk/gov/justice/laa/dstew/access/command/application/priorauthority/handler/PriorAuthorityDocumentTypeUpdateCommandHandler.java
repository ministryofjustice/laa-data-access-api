package uk.gov.justice.laa.dstew.access.command.application.priorauthority.handler;

import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.annotation.InjectEntity;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityAggregate;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDecider;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentTypeUpdateCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDraftStore;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

/** Handles updates to document types in prior-authority drafts. */
@Component
public class PriorAuthorityDocumentTypeUpdateCommandHandler {

  /** Updates the document type for a document in the prior-authority draft and emits the event. */
  @CommandHandler
  public UUID handle(
      PriorAuthorityDocumentTypeUpdateCommand command,
      PriorAuthorityDraftStore draftStore,
      @InjectEntity(idProperty = "priorAuthorityId") PriorAuthorityAggregate priorAuthority,
      EventAppender eventAppender) {

    requireDraftLifecycle(priorAuthority);
    requireActiveDocument(command, priorAuthority);
    PriorAuthorityDataPayload existingDraft = requireDraft(command.priorAuthorityId(), draftStore);
    PriorAuthorityDataPayload updatedPayload =
        existingDraft
            .withSerialisedRequest(command.serialisedRequest())
            .withSubmittedAt(command.occurredAt());
    draftStore.upsert(
        command.priorAuthorityId(),
        existingDraft.applicationId(),
        updatedPayload,
        command.serialisedRequest(),
        command.occurredAt());
    eventAppender.append(PriorAuthorityDecider.decideDocumentTypeUpdated(command));
    return command.documentId();
  }

    private static void requireDraftLifecycle(PriorAuthorityAggregate priorAuthority) {
        if (priorAuthority.getState().isSubmitted()) {
            throw new ValidationException(
                    java.util.List.of("Documents can only be changed on a prior authority draft"));
        }
  }

    private static void requireActiveDocument(
            PriorAuthorityDocumentTypeUpdateCommand command, PriorAuthorityAggregate priorAuthority) {
        priorAuthority.getState().getUploadedDocuments().stream()
                .filter(document -> document.documentId().equals(command.documentId()))
                .filter(document -> !document.deleted())
                .findFirst()
                .orElseThrow(
                        () ->
                                new ResourceNotFoundException(
                                        "Document %s not found for Prior Authority %s"
                                                .formatted(command.documentId(), command.priorAuthorityId())));
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
