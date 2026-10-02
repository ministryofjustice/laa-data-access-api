package uk.gov.justice.laa.dstew.access.command.application.priorauthority.handler;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
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
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityDocument;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.model.PriorAuthorityDocumentType;

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

    PriorAuthorityDocumentType.fromValue(command.documentType());

    PriorAuthorityDataPayload existingDraft =
        draftStore
            .find(command.priorAuthorityId())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Prior Authority draft not found: " + command.priorAuthorityId()));

    List<PriorAuthorityDocument> updatedDocuments = getUpdatedDocuments(command, existingDraft);

    PriorAuthorityContent updatedContent =
        existingDraft.content().withUploadedDocuments(List.copyOf(updatedDocuments));
    PriorAuthorityDataPayload updatedPayload =
        existingDraft
            .withContent(updatedContent)
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

  private static @NonNull List<PriorAuthorityDocument> getUpdatedDocuments(
      PriorAuthorityDocumentTypeUpdateCommand command, PriorAuthorityDataPayload existingDraft) {
    List<PriorAuthorityDocument> updatedDocuments = copyUploadedDocuments(existingDraft);
    int documentIndex =
        IntStream.range(0, updatedDocuments.size())
            .filter(index -> updatedDocuments.get(index).documentId().equals(command.documentId()))
            .findFirst()
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Document %s not found for Prior Authority %s"
                            .formatted(command.documentId(), command.priorAuthorityId())));
    PriorAuthorityDocument existingDocument = updatedDocuments.get(documentIndex);
    updatedDocuments.set(documentIndex, existingDocument.withDocumentType(command.documentType()));
    return updatedDocuments;
  }

  private static List<PriorAuthorityDocument> copyUploadedDocuments(
      PriorAuthorityDataPayload draft) {
    return draft.content().uploadedDocuments() == null
        ? new ArrayList<>()
        : new ArrayList<>(draft.content().uploadedDocuments());
  }
}
