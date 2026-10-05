package uk.gov.justice.laa.dstew.access.command.application.priorauthority.handler;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.annotation.InjectEntity;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityAggregate;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDecider;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentUploadCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDraftStore;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityDocument;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;

/** Handles uploads of documents to a prior-authority draft. */
@Component
@ExcludeFromGeneratedCodeCoverage
public class PriorAuthorityDocumentUploadCommandHandler {

  /** Uploads a document to the prior-authority draft and emits the corresponding event. */
  @CommandHandler
  public UUID handle(
      PriorAuthorityDocumentUploadCommand command,
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

    List<PriorAuthorityDocument> existingDocuments = copyUploadedDocuments(existingDraft);
    existingDocuments.add(
        new PriorAuthorityDocument(
            command.documentId(),
            null,
            command.originalFilename(),
            command.fileType(),
            command.contentType(),
            command.fileSize(),
            command.occurredAt(),
            command.sourceService(),
            command.checksum()));

    PriorAuthorityContent updatedContent =
        existingDraft.content().withUploadedDocuments(List.copyOf(existingDocuments));

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
        PriorAuthorityDecider.decideDocumentUploaded(command, priorAuthority.getApplicationId()));
    return command.documentId();
  }

  private static List<PriorAuthorityDocument> copyUploadedDocuments(
      PriorAuthorityDataPayload draft) {
    return draft.content().uploadedDocuments() == null
        ? new ArrayList<>()
        : new ArrayList<>(draft.content().uploadedDocuments());
  }
}
