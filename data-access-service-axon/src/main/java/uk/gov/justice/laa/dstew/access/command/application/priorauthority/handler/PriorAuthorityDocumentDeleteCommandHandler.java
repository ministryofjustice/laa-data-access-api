package uk.gov.justice.laa.dstew.access.command.application.priorauthority.handler;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.annotation.InjectEntity;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityAggregate;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDecider;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentDeleteCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDraftStore;
import uk.gov.justice.laa.dstew.access.content.priorauthority.EvidenceDocument;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;

/** Handles deletion of documents from a prior-authority draft. */
@Component
public class PriorAuthorityDocumentDeleteCommandHandler {

  /** Deletes a document from the prior-authority draft and emits the corresponding event. */
  @CommandHandler
  public UUID handle(
      PriorAuthorityDocumentDeleteCommand command,
      PriorAuthorityDraftStore draftStore,
      @InjectEntity(idProperty = "priorAuthorityId") PriorAuthorityAggregate priorAuthority,
      EventAppender eventAppender) {

    PriorAuthorityDataPayload existingDraft =
        draftStore
            .find(command.priorAuthorityId())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Prior Authority draft not found: " + command.priorAuthorityId()));

    List<EvidenceDocument> updatedDocuments = copyUploadedDocuments(existingDraft);
    boolean removed =
        updatedDocuments.removeIf(document -> document.documentId().equals(command.documentId()));
    if (!removed) {
      throw new ResourceNotFoundException(
          "Document %s not found for Prior Authority %s"
              .formatted(command.documentId(), command.priorAuthorityId()));
    }

    PriorAuthorityContent updatedContent =
        existingDraft.content().withUploadedDocuments(List.copyOf(updatedDocuments));
    PriorAuthorityDataPayload updatedPayload =
        existingDraft
            .withContent(updatedContent)
            .withSerialisedRequest(command.serialisedRequest());
    draftStore.upsert(
        command.priorAuthorityId(),
        existingDraft.applicationId(),
        updatedPayload,
        command.serialisedRequest(),
        command.occurredAt());
    eventAppender.append(
        PriorAuthorityDecider.decideDocumentDeleted(command, priorAuthority.getApplicationId()));
    return command.documentId();
  }

  private static List<EvidenceDocument> copyUploadedDocuments(PriorAuthorityDataPayload draft) {
    return draft.content().uploadedDocuments() == null
        ? new ArrayList<>()
        : new ArrayList<>(draft.content().uploadedDocuments());
  }
}
